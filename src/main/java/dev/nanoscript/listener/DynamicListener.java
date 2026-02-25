package dev.nanoscript.listener;

import dev.nanoscript.jsengine.*;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * Bukkit event geldiğinde JS callback'i çağıran listener.
 * Artık Rhino yerine NanoEngine kullanıyor.
 */
public class DynamicListener implements Listener {

    private final JSFunction callback;
    private final String eventName;
    private final NanoEngine engine;
    private final Logger logger;

    public DynamicListener(JSFunction callback, String eventName, NanoEngine engine, Logger logger) {
        this.callback  = callback;
        this.eventName = eventName;
        this.engine    = engine;
        this.logger    = logger;
    }

    public void handleEvent(Event event) {
        try {
            // Java Event nesnesini JSValue'ya sar ve callback'e gönder
            JSValue jsEvent = JSValue.wrap(event);
            engine.call(callback, new JSValue[]{jsEvent});
        } catch (JsError e) {
            logger.warning("[NanoScript] " + eventName + " handler hatası: " + e.getMessage());
        } catch (Exception e) {
            logger.warning("[NanoScript] " + eventName + " beklenmedik hata: " + e.getMessage());
        }
    }

    public String getEventName() { return eventName; }
}
