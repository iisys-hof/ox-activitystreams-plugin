# ox-activitystreams-plugin
Activitystreams Plugin for Open-Xchange as an OSGi Bundle.

Configuration file: /src/main/resources/conf/activitystreams.properties

Installation:
1. Import project into Maven-enabled Eclipse
2. Add Open-XChange and JSON Libraries to build path (com.openexchange.configread, com.openexchange.global, com.openexchange.osgi, com.openexchange.server)
3. Export a library jar called "openxchange-activitystreams.jar" (due to OSGi Classpath)
4. Place jar in OX folder "bundles/de.hofuniversity.iisys.ox.activitystreams/"
5. Edit activitystreams.properties to match your setup
6. Place contents of /src/main/resources/ in "bundles/de.hofuniversity.iisys.ox.activitystreams/"
7. Place JSON library jar in "bundles/de.hofuniversity.iisys.ox.activitystreams/lib/" (must be called json-20090211.jar)
8. Place activitystreams.properties in etc/ (in OX directory)
9. echo "/opt/open-xchange/bundles/de.hofuniversity.iisys.ox.activitystreams@start" > osgi/bundle.d/de.hofuniversity.iisys.ox.activitystreams.ini
10. Restart Open-XChange

