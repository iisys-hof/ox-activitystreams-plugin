package de.hofuniversity.iisys.ox.activitystreams;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;

import org.json.JSONObject;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.openexchange.event.CommonEvent;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.User;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.session.Session;

import de.hofuniversity.iisys.ox.activitystreams.extractors.CalendarExtractor;
import de.hofuniversity.iisys.ox.activitystreams.extractors.ContactExtractor;
import de.hofuniversity.iisys.ox.activitystreams.extractors.TaskExtractor;

/**
 * Event handler evaluating Open-Xchange groupware events, generating
 * ActivityStreams 2.5 entries and sending them to an instance of Apache
 * Shindig.
 * Module specific analysis is delegated to implementations of the IExtractor
 * interface.
 */
public class ActivityStreamsEventProcessor implements EventHandler
{
    private static final String ACT_STR_FRAG = "social/rest/activitystreams/";
    
    private final String fShindigUrl;
    private final String fOxUrl;
    
    private final String fLogFile;
    
    private final boolean fLogging;
    private final boolean fLogActivities;
    private final boolean fSendActivities;
    
    private final boolean fCalendarActivities;
    private final boolean fContactActivities;
    private final boolean fTaskActivities;
    private final boolean fRsvpActivities;
    
    private final boolean fFilterPrivFolders;
    private final boolean fFilterSysFolders;
    
    private final ActivityStreamsEventLogger fLogger;
    
    private final CalendarExtractor fCalendarEx;
    private final ContactExtractor fContactEx;
    private final TaskExtractor fTaskEx;
    
    private final JSONObject fGenerator;
    
    private final LinkedList<Object[]> fActivityQueue;
    
    /**
     * Creates an event processor using parameters from its configuration,
     * potentially initializing a debug logger.
     * 
     * @throws Exception if the initialization fails
     */
    public ActivityStreamsEventProcessor() throws Exception
    {
        fActivityQueue = new LinkedList<Object[]>();
        
        //read and set configuration
        Map<String, String> config =
            new ActivityStreamsConfiguration().getConfiguration();
        
        fOxUrl = config.get(ActivityStreamsConfiguration.OX_URL);
        fShindigUrl = config.get(ActivityStreamsConfiguration.SHINDIG_URL);
        fLogFile = config.get(ActivityStreamsConfiguration.LOG_FILE);
        
        fLogging = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.LOGGING_ENABLED));
        fLogActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.LOG_ACTIVITIES));
        fSendActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.SEND_ACTIVITIES));
        
        fCalendarActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.CAL_ACTIVITIES));
        fContactActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.CON_ACTIVITIES));
        fTaskActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.TASK_ACTIVITIES));
        fRsvpActivities = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.RSVP_ACTIVITIES));
        
        fFilterPrivFolders = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.FILTER_PRIV_FOL_CON));
        fFilterSysFolders = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.FILTER_SYSTEM_FOLDERS));
        
        boolean sendInvites = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.SEND_INVITES));
        boolean sendDelContacts = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.CON_DELETIONS));
        boolean filterUnnamed = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.FILTER_UNNAMED));
        
        boolean filterRsvpUpdates = Boolean.parseBoolean(
            config.get(ActivityStreamsConfiguration.FILTER_RSVP_UPDATES));

        //activate logger if configured
        if(fLogging)
        {
            fLogger = new ActivityStreamsEventLogger(fLogFile);
        }
        else
        {
            fLogger = null;
        }
        
        //create extractors
        fCalendarEx = new CalendarExtractor(fOxUrl, sendInvites, filterUnnamed,
            filterRsvpUpdates, this);
        fContactEx = new ContactExtractor(fOxUrl, sendDelContacts,
            filterUnnamed);
        fTaskEx = new TaskExtractor(fOxUrl, filterUnnamed);
        
        //create reusable generator object
        fGenerator = new JSONObject();
        fGenerator.put("id", "open-xchange");
        fGenerator.put("displayName", "Open-Xchange");
        fGenerator.put("objectType", "application");
        fGenerator.put("url", fOxUrl);
    }

    @Override
    public void handleEvent(Event event)
    {
        //log if configured
        if(fLogging)
        {
            fLogger.logEvent(event);
        }
        
        //create activities
        Object eObject = event.getProperty("OX_EVENT");
        Object topObj = event.getProperty("event.topics");
        
        if(eObject != null
            && eObject instanceof CommonEvent)
        {
            CommonEvent cEvent = (CommonEvent) eObject;
            String[] topics = topObj.toString().split("/");
            
            try
            {
                generateActivity(cEvent, topics);
            }
            catch(Exception e)
            {
                e.printStackTrace();
                if(fLogging)
                {
                    fLogger.logOther("exception: " + e.getMessage());
                }
            }
        }
    }
    
    private void generateActivity(CommonEvent event, String[] topics)
        throws Exception
    {
        boolean send = true;
        JSONObject activity = new JSONObject();
        String userId = null;
        
        String action = topics[topics.length - 1];
        String type = topics[topics.length - 2];
        
        //attach user object
        Session session = event.getSession();
        
        if(session != null)
        {
            int contextId = event.getContextId();
            Context context = ContextStorage.getInstance()
                .getContext(contextId);
            
            //retrieve internal user object for the session's ID
            User user = UserStorage.getInstance().getUser(
                session.getUserId(), context);
            
            userId = session.getLogin();
            
            JSONObject actor = new JSONObject();
            actor.put("id", userId);
            actor.put("objectType", "person");
            
            String displayName = user.getGivenName() + " " + user.getSurname();
            actor.put("displayName", displayName);
            
            activity.put("actor", actor);
        }
        
        //determine verb for action
        String verb = "Post";
        switch(event.getAction())
        {
            case CommonEvent.INSERT:
                verb = "add";
                break;
                
            case CommonEvent.UPDATE:
                verb = "update";
                break;
            
            case CommonEvent.DELETE:
                verb = "remove";
                break;
            
            //TODO: better verb? - none available
            case CommonEvent.MOVE:
                verb = "update";
                break;
                
            case CommonEvent.CONFIRM_ACCEPTED:
                if(!fRsvpActivities)
                {
                    send = false;
                }
                verb = "rsvp-yes";
                break;
                
            case CommonEvent.CONFIRM_DECLINED:
                if(!fRsvpActivities)
                {
                    send = false;
                }
                verb = "rsvp-no";
                break;
                
            case CommonEvent.CONFIRM_TENTATIVE:
                if(!fRsvpActivities)
                {
                    send = false;
                }
                verb = "rsvp-maybe";
                break;

                //TODO: not right, filter?
            case CommonEvent.CONFIRM_WAITING:
                verb = "request";
                break;
        }
        activity.put("verb", verb);
        
        //extract event-specific details
        switch(type)
        {
            case "contact":
                if(fContactActivities)
                {
                    send = fContactEx.extract(activity, event, action);
                }
                else
                {
                    send = false;
                }
                break;
                
            case "appointment":
                if(fCalendarActivities)
                {
                    send = fCalendarEx.extract(activity, event, action);
                }
                else
                {
                    send = false;
                }
                break;
                
            case "task":
                if(fTaskActivities)
                {
                    send = fTaskEx.extract(activity, event, action);
                }
                else
                {
                    send = false;
                }
                break;
                
            case "infostore":
                //filtered
                send = false;
                break;
                
            case "folder":
                //filtered at the moment
                send = false;
                break;
            
            default:
                if(fLogging)
                {
                    fLogger.logOther("unknown type: " + type);
                }
                send = false;
                break;
        }
        
        //general folder based filters
        Object folderObj = event.getSourceFolder();
        FolderObject folder = null;
        if(folderObj != null
            && folderObj instanceof FolderObject)
        {
            folder = (FolderObject) folderObj;
        }
        
        //filter events from private folders if configured
        if(fFilterPrivFolders
            && folder != null
            && folder.getType() == FolderObject.PRIVATE)
        {
            send = false;
        }
        
        //filter events from system folders if configured
        if(fFilterSysFolders
            && folder != null
            && folder.getType() == FolderObject.SYSTEM_TYPE)
        {
            send = false;
        }
        
        //add generator
        activity.put("generator", fGenerator);
        
        if(fLogActivities)
        {
            fLogger.logOther(activity.toString());
        }
        
        //send activity to shindig
        if(fSendActivities && send)
        {
            sendActivity(activity, userId);
        }
        
        //send queued activities
        if(fSendActivities)
        {
            sendQueue();
        }
    }
    
    /**
     * @return Open-Xchange generator object for activities
     */
    public JSONObject getGenerator()
    {
        return fGenerator;
    }
    
    /**
     * Sends an activity to the configured Apache Shindig server, in the name
     * the given user.
     * 
     * @param activity activity to send to the server
     * @param user user the activity is for
     * @throws Exception if sending fails
     */
    public void sendActivity(JSONObject activity, String user) throws Exception
    {
        final String json = activity.toString();
        
        //set parameters for sending
        URL shindigUrl = new URL(fShindigUrl + ACT_STR_FRAG + user +
                "/@self");
        final HttpURLConnection connection =
            (HttpURLConnection) shindigUrl.openConnection();
        
        connection.setRequestMethod("POST");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(
            json.length()));
        
        //send JSON activity
        OutputStreamWriter writer = new OutputStreamWriter(
            connection.getOutputStream(), "UTF-8");
        writer.write(json);
        writer.flush();
        
        //read reply
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));
        
        String line = reader.readLine();
        while(line != null)
        {
            line = reader.readLine();
        }
        //TODO: evaluate answer?
        
        reader.close();
    }
    
    /**
     * Queues an activity to be sent after the next main activity is sent;
     * 
     * @param activity activity to enqueue
     * @param user user to send the activity for
     */
    public void queueActivity(JSONObject activity, String user)
    {
        Object[] vector = new Object[2];
        vector[0] = activity;
        vector[1] = user;
        
        synchronized(fActivityQueue)
        {
            fActivityQueue.add(vector);
        }
    }
    
    private void sendQueue() throws Exception
    {
        Object[] vector = null;
        
        while(!fActivityQueue.isEmpty())
        {
            synchronized(fActivityQueue)
            {
                vector = fActivityQueue.pop();
            }
            
            if(fLogActivities)
            {
                fLogger.logOther(vector[0].toString());
            }
            
            sendActivity((JSONObject)vector[0], (String)vector[1]);
        }
        
        //TODO: empty queue if sending fails?
    }
}
