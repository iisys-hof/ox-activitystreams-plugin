package de.hofuniversity.iisys.ox.activitystreams.extractors;

import org.json.JSONObject;

import com.openexchange.event.CommonEvent;
import com.openexchange.groupware.container.Contact;
import com.openexchange.groupware.container.FolderObject;


/**
 * Extractor for generating activities for contacts.
 */
public class ContactExtractor implements IExtractor
{
    private static final String CONTACTS_FRAG = "#!!&app=io.ox/contacts";
    private static final String FOLDER_FRAG = "&folder=";
    private static final String ID_FRAG = "&id=";
    
    private final String fOxUrl;
    
    private final boolean fSendDeleted;
    private final boolean fFilterUnnamed;
    
    /**
     * Creates a contact event extractor generating links to the given
     * instance URL of Open-Xchange.
     * The given URL should not be null.
     * 
     * @param oxUrl Open-Xchange instance URL
     * @param sendDeleted whether to send activities for contact deletions
     * @param filterUnnamed whether to filter activities with unnamed entities
     */
    public ContactExtractor(String oxUrl, boolean sendDeleted,
        boolean filterUnnamed)
    {
        fOxUrl = oxUrl;
        fSendDeleted = sendDeleted;
        fFilterUnnamed = filterUnnamed;
    }

    @Override
    public boolean extract(JSONObject activity, CommonEvent event,
        String action) throws Exception
    {
        boolean send = true;
        
        String targetId = null;
        
        //target: folder
        Object folderObj = event.getSourceFolder();
        if(folderObj != null
            && folderObj instanceof FolderObject)
        {
            FolderObject folder = (FolderObject) folderObj;
            targetId = Integer.toString(folder.getObjectID());
            
            JSONObject target = new JSONObject();
            
            target.put("id", targetId);
            target.put("objectType", "open-xchange-contacts-folder");
            target.put("displayName", folder.getFolderName());
            
            String url = fOxUrl + CONTACTS_FRAG + FOLDER_FRAG + targetId;
            target.put("url", url);
            
            activity.put("target", target);
        }
        
        //object: contact object
        Object actionObj = event.getActionObj();
        
        if(actionObj != null
            && actionObj instanceof Contact)
        {
            Contact contact = (Contact) actionObj;
            
            //don't send if it's marked private
            //TODO: doesn't work for deleted contacts
            if(contact.getPrivateFlag())
            {
                send = false;
            }
            
            //filter out entries with missing titles (deleted folders etc.)
            if(fFilterUnnamed
                && (contact.getDisplayName() == null
                || contact.getDisplayName().isEmpty()))
            {
                send = false;
            }
            
            JSONObject object = new JSONObject();
            object.put("id", contact.getObjectID());
            object.put("objectType", "open-xchange-contact");
            
            //TODO: better solution?
            if(contact.getDisplayName() != null)
            {
                String displayName = contact.getGivenName() + " "
                    + contact.getSurName();
                
                //optional title
                //TODO: suffixes?
                if(contact.getTitle() != null)
                {
                    displayName = contact.getTitle() + " " + displayName;
                }
                
                object.put("displayName", displayName);
            }
            else
            {
                object.put("displayName", "Kontakt");
            }
            
            //TODO: names for deleted entries?
            
            String url = fOxUrl + CONTACTS_FRAG + FOLDER_FRAG + targetId
                + ID_FRAG + targetId + "." + contact.getObjectID();
            object.put("url", url);
            
            activity.put("object", object);
        }
        else if(actionObj != null)
        {
            throw new Exception("contact object of class "
                + actionObj.getClass());
            
//            send = false;
        }
        else
        {
            send = false;
        }
        
        //don't send activities for deletions since data is missing
        if(!fSendDeleted
            && event.getAction() == CommonEvent.DELETE)
        {
            send = false;
        }
        
        return send;
    }

}
