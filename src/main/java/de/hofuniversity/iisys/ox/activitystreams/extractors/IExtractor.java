package de.hofuniversity.iisys.ox.activitystreams.extractors;

import org.json.JSONObject;
import com.openexchange.event.CommonEvent;

/**
 * Interface for classes that extract additional information from events,
 * enriching a given activity.
 */
public interface IExtractor
{
    /**
     * Relays an incoming event and a pre-generated activity to the extractor,
     * expecting it to add additional details such as an object and a target.
     * The extractor should also determine whether an activity should be sent
     * or filtered out based on its own criteria.
     * 
     * @param activity activity to enrich
     * @param event event to evaluate
     * @param action action performed
     * @return whether the activity should be sent
     * @throws Exception if the evaluation fails
     */
    public boolean extract(JSONObject activity, CommonEvent event,
        String action) throws Exception;
}
