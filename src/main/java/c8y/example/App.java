package c8y.example;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;
import com.cumulocity.microservice.settings.service.MicroserviceSettingsService;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.ID;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.event.EventRepresentation;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.alarm.AlarmApi;
import com.cumulocity.sdk.client.event.EventApi;
import com.cumulocity.sdk.client.event.EventCollection;
import com.cumulocity.sdk.client.event.PagedEventCollectionRepresentation;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.measurement.MeasurementApi;

import c8y.IsDevice;


@MicroserviceApplication
@RestController
public class App{
		
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @RequestMapping("hello")
    public String greeting(@RequestParam(value = "name", defaultValue = "world") String name) {
        return "hello " + name + "!";
    }

    // You need the inventory API to handle managed objects e.g. creation. You will find this class within the C8Y java client library.
    private final InventoryApi inventoryApi;
    // you need the identity API to handle the external ID e.g. IMEI of a managed object. You will find this class within the C8Y java client library.
    private final IdentityApi identityApi;
    
    // you need the measurement API to handle measurements. You will find this class within the C8Y java client library.
    private final MeasurementApi measurementApi;
    
    // you need the alarm API to handle measurements.
    private final AlarmApi alarmApi;
    
    // you need the event API to handle measurements.
    private final EventApi eventApi;
    
    // Microservice subscription
    private final MicroserviceSubscriptionsService subscriptionService;
        
    // To access the tenant options
    private final MicroserviceSettingsService microserviceSettingsService;
    
    @Autowired
    public App( InventoryApi inventoryApi, 
    			IdentityApi identityApi, 
    			MicroserviceSubscriptionsService subscriptionService,
    			MeasurementApi measurementApi,
    			MicroserviceSettingsService microserviceSettingsService,
    			AlarmApi alarmApi,
    			EventApi eventApi) {
        this.inventoryApi = inventoryApi;
        this.identityApi = identityApi;
        this.subscriptionService = subscriptionService;
        this.measurementApi = measurementApi;
        this.microserviceSettingsService = microserviceSettingsService;
        this.alarmApi = alarmApi;
        this.eventApi = eventApi;
    }
    
    // Create every x sec a new measurement
    @Scheduled(initialDelay=10000, fixedDelay=5000)
    public void startThread() {
    	subscriptionService.runForEachTenant(new Runnable() {
			@Override
			public void run() {
		    	try {
		    		checkDoorStatus();	    		
				} catch (Exception e) {
					e.printStackTrace();
				} 
			}
		});
    }
        
    // Create a new managed object + external ID (if not existing)  
    private ManagedObjectRepresentation resolveManagedObject() {
       	
    	try {
        	// check if managed object is existing. create a new one if the managed object is not existing
    		ExternalIDRepresentation externalIDRepresentation = identityApi.getExternalId(new ID("c8y_Serial", "Microservice-Part8_externalId"));
			return externalIDRepresentation.getManagedObject();    	    	

    	} catch(SDKException e) {
    		    		
    		// create a new managed object
			ManagedObjectRepresentation newManagedObject = new ManagedObjectRepresentation();
	    	newManagedObject.setName("Microservice-Part8");
	    	newManagedObject.setType("Microservice-Part8");
	    	newManagedObject.set(new IsDevice());	    	
	    	ManagedObjectRepresentation createdManagedObject = inventoryApi.create(newManagedObject);
	    	
	    	// create an external id and add the external id to an existing managed object
	    	ExternalIDRepresentation externalIDRepresentation = new ExternalIDRepresentation();
	    	// Definition of the external id
	    	externalIDRepresentation.setExternalId("Microservice-Part8_externalId");
	    	// Assign the external id to an existing managed object
	    	externalIDRepresentation.setManagedObject(createdManagedObject);
	    	// Definition of the serial
	    	externalIDRepresentation.setType("c8y_Serial");
	    	// Creation of the external id
	    	identityApi.create(externalIDRepresentation);
	    	
	    	return createdManagedObject;
    	}
    }
    
    public void checkDoorStatus() {    	
    	// Simulator for opening and closing of a door
    	Random r = new Random();
    	int i = r.nextInt(100);
    	if(i%2==0) {
    		// door open -> create a new event
    		
    		// Managed object representation will give you access to the ID of the managed object
    		ManagedObjectRepresentation managedObjectRepresentation = resolveManagedObject();

    		// Event representation object
    		EventRepresentation eventRepresentation = new EventRepresentation();
    		
    		// set the event properties
    		eventRepresentation.setDateTime(new DateTime());
    		// add event to a managed object
    		eventRepresentation.setSource(managedObjectRepresentation);
    		eventRepresentation.setText("Door open");
    		eventRepresentation.setType("Event_type");
    		
    		// create a new event
    		eventApi.create(eventRepresentation);
    	} else {
    		// door closed -> do nothing
    	}
    }

    // get event by id
    @RequestMapping("getEventById")
    public String getEventById(@RequestParam(value = "eventId") String eventId) {
		if(eventId.length()>=1) {
			try {
				// Use GId to transform the given id to a global c8y id
				EventRepresentation eventRepresentation = eventApi.getEvent(GId.asGId(eventId));
				
				return eventRepresentation.toJSON();
			} catch(Exception e) {
				return "Event with the id "+eventId+" does not exist.";
			}
		}
		return "Insert a valid event id.";
    }

    // get all events
    @RequestMapping("getAllEvents")
    public List<EventRepresentation> getAllEvents() {
    	
    	EventCollection eventCollection = eventApi.getEvents();
    	PagedEventCollectionRepresentation pagedEventCollectionRepresentation = eventCollection.get();   	
    	Iterator<EventRepresentation> it = pagedEventCollectionRepresentation.allPages().iterator();
    	
    	List<EventRepresentation> eventRepresentationList = new ArrayList<>();	
    	while(it.hasNext()) {
    		eventRepresentationList.add(it.next());
    	}
    	
    	return eventRepresentationList;
    }
    
	// delete all events
	@RequestMapping("deleteAllEvents")
	public void deleteAllAlarms() {
    	EventCollection eventCollection = eventApi.getEvents();
    	PagedEventCollectionRepresentation pagedEventCollectionRepresentation = eventCollection.get();
    	
    	List<EventRepresentation> eventRepresentations = pagedEventCollectionRepresentation.getEvents();
    	for(EventRepresentation eventRepresentation : eventRepresentations) {
    		eventApi.delete(eventRepresentation);
    	}
	}

}