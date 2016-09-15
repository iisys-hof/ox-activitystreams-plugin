package de.hofuniversity.iisys.ox.activitystreams.extractors;

import org.json.JSONObject;

import com.openexchange.event.CommonEvent;
import com.openexchange.groupware.container.FolderObject;
import com.openexchange.groupware.tasks.Task;

/**
 * Extractor for generating activities for tasks.
 */
public class TaskExtractor implements IExtractor
{
    private static final String TASKS_FRAG = "#!!&app=io.ox/tasks";
    private static final String FOLDER_FRAG = "&folder=";
    private static final String ID_FRAG = "&id=";
    
    private final String fOxUrl;
    
    private final boolean fFilterUnnamed;
    
    /**
     * Creates a task event extractor generating links to the given
     * instance URL of Open-Xchange.
     * The given URL should not be null.
     * 
     * @param oxUrl Open-Xchange instance URL
     * @param filterUnnamed whether to filter activities with unnamed entities
     */
    public TaskExtractor(String oxUrl, boolean filterUnnamed)
    {
        fOxUrl = oxUrl;
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
            
            target.put("id", folder.getObjectID());
            target.put("objectType", "open-xchange-tasks-folder");
            target.put("displayName", folder.getFolderName());
            
            String url = fOxUrl + TASKS_FRAG + FOLDER_FRAG + targetId;
            target.put("url", url);
            
            activity.put("target", target);
        }
        
        //object: task object
        Object taskObj = event.getActionObj();
        
        if(taskObj != null
            && taskObj instanceof Task)
        {
            Task task = (Task) event.getActionObj();
            
            //don't send if it's marked private
            if(task.getPrivateFlag())
            {
                send = false;
            }
            
            //filter out entries with missing titles (deleted folders)
            if(fFilterUnnamed
                && (task.getTitle() == null
                || task.getTitle().isEmpty()))
            {
                send = false;
            }
            
            JSONObject object = new JSONObject();
            object.put("id", task.getObjectID());
            object.put("objectType", "open-xchange-task");
            object.put("displayName", task.getTitle());
            
            String url = fOxUrl + TASKS_FRAG + FOLDER_FRAG + targetId
                + ID_FRAG + targetId + "." + task.getObjectID();
            object.put("url", url);
            
            activity.put("object", object);
        }
        else if(taskObj != null)
        {
            throw new Exception("task object of class "
                + taskObj.getClass());
            
//            send = false;
        }
        else
        {
            send = false;
        }
        
        return send;
    }

}
