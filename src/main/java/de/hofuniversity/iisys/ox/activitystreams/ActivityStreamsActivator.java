package de.hofuniversity.iisys.ox.activitystreams;

import java.util.Dictionary;
import java.util.Hashtable;

import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import com.openexchange.config.ConfigurationService;
import com.openexchange.osgi.HousekeepingActivator;

/**
 * OSGi activator starting, stopping, registering and deregistering the
 * activitystreams event handler.
 */
public class ActivityStreamsActivator extends HousekeepingActivator
{
    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(ActivityStreamsActivator.class);

    @Override
    protected Class<?>[] getNeededServices()
    {
        return new Class<?>[] { ConfigurationService.class };
    }

    @Override
    protected void handleAvailability(final Class<?> clazz)
    {
        LOG.warn("Absent service: {}", clazz.getName());
    }

    @Override
    protected void handleUnavailability(final Class<?> clazz)
    {
        LOG.info("Re-available service: {}", clazz.getName());
    }

    @Override
    protected void startBundle() throws Exception
    {
        try
        {
            //register service
            Services.setServiceLookup(this);
            
            //register event handling
            final Dictionary<String, Object> serviceProperties =
                new Hashtable<String, Object>(1);
            serviceProperties.put(EventConstants.EVENT_TOPIC,
                new String[]
                { "com/openexchange/groupware/*",
                "com/openexchange/groupware/infostore/*" });
            registerService(EventHandler.class, new ActivityStreamsEventProcessor(),
                serviceProperties);
        }
        catch (final Throwable t)
        {
            LOG.error("", t);
            throw t instanceof Exception ? (Exception) t : new Exception(t);
        }
    }

    @Override
    protected void stopBundle() throws Exception
    {
        try
        {
            cleanUp();
            Services.setServiceLookup(null);
        }
        catch (final Throwable t)
        {
            LOG.error("", t);
            throw t instanceof Exception ? (Exception) t : new Exception(t);
        }
    }
}
