package de.hofuniversity.iisys.ox.activitystreams.extractors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.JSONObject;

import com.openexchange.event.CommonEvent;
import com.openexchange.groupware.calendar.CalendarDataObject;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.container.UserParticipant;
import com.openexchange.groupware.contexts.Context;
import com.openexchange.groupware.contexts.impl.ContextStorage;
import com.openexchange.groupware.ldap.User;
import com.openexchange.groupware.ldap.UserStorage;

import de.hofuniversity.iisys.ox.activitystreams.ActivityStreamsEventProcessor;

/**
 * Extractor for generating activities for calendar entries.
 */
public class CalendarExtractor implements IExtractor
{
    private static final long RSVP_COOLDOWN = 1000L;
    
    private static final String CALENDAR_FRAG = "#!!&app=io.ox/calendar";
    private static final String FOLDER_FRAG = "&folder=";
    private static final String ID_FRAG = "&id=";
    
    private final String fOxUrl;
    private final ActivityStreamsEventProcessor fAsProc;
    
    private final boolean fSendInvites;
    private final boolean fFilterUnnamed;
    private final boolean fFilterRsvpUpdates;
    
    private final Map<Integer, long[]> fRsvpCooldowns;
    
    /**
     * Creates a calendar event extractor generating links to the given
     * instance URL of Open-Xchange and if configured sending invitation
     * activities using the given event processor.
     * The given URL should not be null.
     * The given ActivityStreamsEventProcessor must not be null.
     * 
     * @param oxUrl Open-Xchange instance URL
     * @param sendInvites whether to generate invite activities
     * @param filterUnnamed whether to filter activities with unnamed entities
     * @param filterRsvpUpdates whether to filter updates following rsvp events
     * @param asProc activity streams processor to send activities with
     */
    public CalendarExtractor(String oxUrl, boolean sendInvites,
        boolean filterUnnamed, boolean filterRsvpUpdates,
        ActivityStreamsEventProcessor asProc)
    {
        fOxUrl = oxUrl;
        fAsProc = asProc;
        fSendInvites = sendInvites;
        fFilterUnnamed = filterUnnamed;
        fFilterRsvpUpdates = filterRsvpUpdates;
        
        fRsvpCooldowns = new HashMap<Integer, long[]>();
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
            target.put("objectType", "open-xchange-calendar-folder");
            target.put("displayName", folder.getFolderName());
            
            String url = fOxUrl + CALENDAR_FRAG + FOLDER_FRAG + targetId;
            target.put("url", url);
            
            activity.put("target", target);
        }
        
        //object: calendar entry
        Object dataObj = event.getActionObj();
        
        if(dataObj != null
            && dataObj instanceof CalendarDataObject)
        {
            CalendarDataObject calObj = (CalendarDataObject) dataObj;
            
            //don't send if it's marked private
            if(calObj.getPrivateFlag())
            {
                send = false;
            }
            
            //no activities for repeating events
            //TODO: check
            if(calObj.isSpecificOcurrence() && !calObj.isMaster())
            {
                send = false;
            }
            
            //filter out entries with missing titles (deleted folders)
            if(fFilterUnnamed
                && (calObj.getTitle() == null
                || calObj.getTitle().isEmpty()))
            {
                send = false;
            }
            
            JSONObject object = new JSONObject();
            object.put("id", calObj.getObjectID());
            object.put("objectType", "open-xchange-appointment");
            object.put("displayName", calObj.getTitle());
            
            String url = fOxUrl + CALENDAR_FRAG + FOLDER_FRAG + targetId
                + ID_FRAG + targetId + "." + calObj.getObjectID();
            object.put("url", url);
            
            activity.put("object", object);
            
            
            //check for required invitation activities
            //TODO: check if mails are to be sent
            if(send
                && event.getAction() == CommonEvent.INSERT
                && fSendInvites)
            {
                checkInvitations(activity, event, calObj);
            }
            
            //handle potentially blocked updates following rsvp events
            if(fFilterRsvpUpdates)
            {
                send = handleRsvpCooldown(event.getUserId(),
                    calObj.getObjectID(), event);
            }
        }
        else if(dataObj != null)
        {
            throw new Exception("calendar object of class "
                + dataObj.getClass());
            
//            send = false;
        }
        else
        {
            send = false;
        }
        
        return send;
    }

    private void checkInvitations(JSONObject activity, CommonEvent event,
        CalendarDataObject calObj) throws Exception
    {
        JSONObject actor = activity.getJSONObject("actor");
        int contextId = event.getContextId();
        Context context = ContextStorage.getInstance()
            .getContext(contextId);
        
        Map<Integer, Set<Integer>> userFolders =
            event.getAffectedUsersWithFolder();
        
        UserParticipant[] uParts = calObj.getUsers();
        int organId = calObj.getOrganizerId();
        
        int userId = -1;
        int folderId = 0;
        Set<Integer> folderIds = null;
        for(UserParticipant up : uParts)
        {
            userId = up.getIdentifier();
            
            if(up.getIdentifier() != organId)
            {
                //determine which folder is affected for this user
                folderIds = userFolders.get(userId);
                if(folderIds != null
                    && folderIds.size() > 0)
                {
                    folderId = folderIds.iterator().next();
                }
                else
                {
                    folderId = 0;
                }
                
                //generate and queue invitation activity
                generateInviteActivity(actor, userId, folderId, calObj,
                    context);
            }
        }
    }
    
    private boolean handleRsvpCooldown(int userId, int entryId,
        CommonEvent event)
    {
        boolean send = true;
        //TODO: incorporate user ID causing the update
        
        synchronized(fRsvpCooldowns)
        {
            //delete old cooldowns
            if(!fRsvpCooldowns.isEmpty())
            {
                Set<Integer> toRemove = new HashSet<Integer>();

                long threshold = System.currentTimeMillis() - RSVP_COOLDOWN;
                
                //first value: user ID, second value: timestamp
                long[] values = null;
                
                for(Entry<Integer, long[]> coolE : fRsvpCooldowns.entrySet())
                {
                    values = coolE.getValue();
                    
                    if(values[1] < threshold)
                    {
                        toRemove.add(coolE.getKey());
                    }
                }
                
                for(Integer key : toRemove)
                {
                    fRsvpCooldowns.remove(key);
                }
            }
            
            //for updates, check if there is a cooldown active
            if(event.getAction() == CommonEvent.UPDATE)
            {
                long[] cooldown = fRsvpCooldowns.get(entryId);
                
                //only block for user who just responded
                if(cooldown != null
                    && cooldown[0] == userId)
                {
                    send = false;
                }
            }
            //for rsvp events, start a new cooldown period
            else if(event.getAction() == CommonEvent.CONFIRM_ACCEPTED
                || event.getAction() == CommonEvent.CONFIRM_DECLINED
                || event.getAction() == CommonEvent.CONFIRM_TENTATIVE
                || event.getAction() == CommonEvent.CONFIRM_WAITING)
            {
                long[] cooldown = new long[2];
                cooldown[0] = userId;
                cooldown[1] = System.currentTimeMillis();
                
                fRsvpCooldowns.put(entryId, cooldown);
            }
        }
        
        return send;
    }
    
    private void generateInviteActivity(JSONObject actor, int userId,
        int folderId, CalendarDataObject calObj, Context context)
        throws Exception
    {
        //add predefined parts
        JSONObject activity = new JSONObject();
        activity.put("actor", actor);
        activity.put("generator", fAsProc.getGenerator());
        activity.put("verb", "invite");
        
        //add the invited user
        User notifiedUser = UserStorage.getInstance().getUser(userId, context);
        JSONObject object = new JSONObject();
        
        object.put("id", notifiedUser.getLoginInfo());
        object.put("objectType", "person");

        String displayName = notifiedUser.getGivenName() + " "
            + notifiedUser.getSurname();
        object.put("displayName", displayName);
        
        activity.put("object", object);
        
        //generate updated target
        JSONObject target = new JSONObject();
        target.put("id", calObj.getObjectID());
        target.put("displayName", calObj.getTitle());
        target.put("objectType", "open-xchange-appointment");
        
        String url = fOxUrl + CALENDAR_FRAG + FOLDER_FRAG + folderId
            + ID_FRAG + folderId + "." + calObj.getObjectID();
        target.put("url", url);
        
        activity.put("target", target);

        //send activity through inviting user
        String user = actor.getString("id");
        fAsProc.queueActivity(activity, user);
    }
}
