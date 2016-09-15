package de.hofuniversity.iisys.ox.activitystreams;

import java.util.HashMap;
import java.util.Map;

import com.openexchange.config.ConfigurationService;

/**
 * Configuration helper class containing property keys and reading
 * configuration values through Open-Xchange's ConfigurationService.
 */
public class ActivityStreamsConfiguration
{
    //unique prefix for all configuration options
    private static final String
        PREFIX = "de.hofuniversity.iisys.ox.activitystreams.";
    
    public static final String OX_URL = "open-xchange_url";
    public static final String SHINDIG_URL = "shindig_url";
    
    public static final String LOGGING_ENABLED = "logging_enabled";
    public static final String LOG_FILE = "log_file";
    public static final String LOG_ACTIVITIES = "log_activities";

    public static final String SEND_ACTIVITIES = "send_activities";
    public static final String CAL_ACTIVITIES = "calendar_activities";
    public static final String CON_ACTIVITIES = "contact_activities";
    public static final String TASK_ACTIVITIES = "task_activities";

    public static final String RSVP_ACTIVITIES = "rsvp_activities";
    
    public static final String CON_DELETIONS = "contact_deletions";
    public static final String SEND_INVITES = "send_invites";
    
    public static final String FILTER_UNNAMED = "filter_unnamed";
    public static final String FILTER_RSVP_UPDATES = "filter_rsvp_updates";
    public static final String FILTER_PRIV_FOL_CON =
        "filter_private_folder_contents";
    public static final String FILTER_SYSTEM_FOLDERS = "filter_system_folders";
    
    
    private static final String[] OPTIONS = {OX_URL, SHINDIG_URL,
        LOGGING_ENABLED, LOG_FILE, LOG_ACTIVITIES, SEND_ACTIVITIES,
        CAL_ACTIVITIES, CON_ACTIVITIES, TASK_ACTIVITIES, RSVP_ACTIVITIES,
        CON_DELETIONS, SEND_INVITES, FILTER_UNNAMED, FILTER_RSVP_UPDATES,
        FILTER_PRIV_FOL_CON, FILTER_SYSTEM_FOLDERS};
    
    /**
     * Reads the configuration, extracting property values into a key-value
     * map which is then returned.
     * 
     * @return map with configuration values, without the package prefix
     */
    public Map<String, String> getConfiguration()
    {
        Map<String, String> config = new HashMap<String, String>();
        
        ConfigurationService configService = Services.optService(
            ConfigurationService.class);
        
        String key = null;
        String value = null;
        for(String option : OPTIONS)
        {
            key = PREFIX + option;
            value = configService.getProperty(key);
            config.put(option, value);
        }
        
        return config;
    }
}
