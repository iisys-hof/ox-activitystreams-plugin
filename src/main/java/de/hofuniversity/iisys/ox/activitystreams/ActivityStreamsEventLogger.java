package de.hofuniversity.iisys.ox.activitystreams;

import java.io.File;
import java.io.PrintWriter;

import org.osgi.service.event.Event;

import com.openexchange.event.CommonEvent;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.User;
import com.openexchange.groupware.ldap.UserStorage;
import com.openexchange.session.Session;

/**
 * Utility class for development and debugging logging all passed events and
 * other Strings to a file.
 */
public class ActivityStreamsEventLogger
{
    private final String fLogFile;
    private final PrintWriter fWriter;
    
    private int fNumber;
    
    public ActivityStreamsEventLogger(String logFile) throws Exception
    {
        fLogFile = logFile;
        
        File file = new File(fLogFile);
        if(!file.exists())
        {
            file.createNewFile();
        }
        
        fWriter = new PrintWriter(file);
        fNumber = 0;
    }
    
    public void logOther(String message)
    {
        fWriter.println("\n" + message + "\n");
        fWriter.flush();
    }
    
    public void logEvent(Event event)
    {
        fWriter.println("event " + ++fNumber);
        fWriter.println("event.class: " + event.getClass());

        fWriter.println("\nproperties:");
        Object property = null;
        for(String key : event.getPropertyNames())
        {
            property = event.getProperty(key);
            
            if(property instanceof CommonEvent)
            {
                fWriter.println("\ncommon event (" + key + "):");
                process((CommonEvent)property);
                fWriter.println();
            }
            else
            {
                fWriter.println(key + ": " + property);
                fWriter.println(key + ".class: " + property.getClass());
            }
        }
        
        if(event instanceof CommonEvent)
        {
            fWriter.println("\ncommon event:");
            process((CommonEvent)event);
        }
        
        fWriter.println("\n\n");
        fWriter.flush();
    }
    
    public void process(CommonEvent event)
    {
        String action = null;
        switch(event.getAction())
        {
            case CommonEvent.INSERT:
                action = "insert";
                break;
                
            case CommonEvent.UPDATE:
                action = "update";
                break;
            
            case CommonEvent.DELETE:
                action = "delete";
                break;
                
            case CommonEvent.MOVE:
                action = "move";
                break;
                
            case CommonEvent.CONFIRM_ACCEPTED:
                action = "confirm_accepted";
                break;
                
            case CommonEvent.CONFIRM_DECLINED:
                action = "confirm_declined";
                break;
                
            case CommonEvent.CONFIRM_TENTATIVE:
                action = "confirm_tentative";
                break;
                
            case CommonEvent.CONFIRM_WAITING:
                action = "confirm_waiting";
                break;
                
            default:
                action = Integer.toString(event.getAction());
                break;
        }
        
        Object actionObject = event.getActionObj();
        Session session = event.getSession();
        
        try
        {
            fWriter.println("action: " + action);

            fWriter.println("actionObject: " + actionObject);
            if(actionObject != null)
            {
                fWriter.println("actionObject class: " + actionObject.getClass());
            }
            
            fWriter.println("affectedUsersWithFolder: " + event.getAffectedUsersWithFolder());

            fWriter.println("sourceFolder: " + event.getSourceFolder());
            
            fWriter.println("destinationFolder: " + event.getDestinationFolder());
            if(event.getDestinationFolder() != null)
            {
                fWriter.println("destinationFolder.class: "
                    + event.getDestinationFolder().getClass());
            }
            
            fWriter.println("module: " + event.getModule());
            
            fWriter.println("userId: " + event.getUserId());
            
            fWriter.println("oldObject: " + event.getOldObj());
            if(event.getOldObj() != null)
            {
                fWriter.println("oldObject.class: " + event.getOldObj().getClass());
            }
            
            fWriter.println("session: " + session);
            if(session != null)
            {
                fWriter.println("session.authId: " + session.getAuthId());
                fWriter.println("session.client: " + session.getClient());
                fWriter.println("session.contextId: " + session.getContextId());
                fWriter.println("session.hash: " + session.getHash());
                fWriter.println("session.localIp: " + session.getLocalIp());
                fWriter.println("session.login: " + session.getLogin());
                fWriter.println("session.loginName: " + session.getLoginName());
                fWriter.println("session.password: " + session.getPassword());
                fWriter.println("session.randomToken: " + session.getRandomToken());
                fWriter.println("session.secret: " + session.getSecret());
                fWriter.println("session.sessionID: " + session.getSessionID());
                fWriter.println("session.userId: " + session.getUserId());
                fWriter.println("session.userLogin: " + session.getUserlogin());
            }
            
            int contextId = event.getContextId();
            Context context = ContextStorage.getInstance().getContext(contextId);
            fWriter.println("context: " + context);
            if(context != null)
            {
                fWriter.println("context.contextId: " + context.getContextId());
                fWriter.println("context.filestoreName: " + context.getFilestoreName());
                fWriter.println("context.name: " + context.getName());
            }
            
            User user = UserStorage.getInstance().getUser(
                session.getUserId(), context);
            fWriter.println("user: " + user);
            if(user != null)
            {
                fWriter.println("user.contactId: " + user.getContactId());
                fWriter.println("user.displayName: " + user.getDisplayName());
                fWriter.println("user.givenName: " + user.getGivenName());
                fWriter.println("user.id: " + user.getId());
                fWriter.println("user.imapLogin: " + user.getImapLogin());
                fWriter.println("user.imapServer: " + user.getImapServer());
                fWriter.println("user.loginInfo: " + user.getLoginInfo());
                fWriter.println("user.mail: " + user.getMail());
                fWriter.println("user.mailDomain: " + user.getMailDomain());
                fWriter.println("user.passwordMech: " + user.getPasswordMech());
                fWriter.println("user.preferredLanguage: " + user.getPreferredLanguage());
                fWriter.println("user.smtpServer: " + user.getSmtpServer());
                fWriter.println("user.surname: " + user.getSurname());
                fWriter.println("user.timeZone: " + user.getTimeZone());
                fWriter.println("user.userPassword: " + user.getUserPassword());
                fWriter.println("user.aliases: " + user.getAliases());
                fWriter.println("user.attributes: " + user.getAttributes());
                fWriter.println("user.groups: " + user.getGroups());
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
