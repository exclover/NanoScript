package dev.nanoscript.listener;

import dev.nanoscript.jsengine.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.logging.Logger;

/**
 * cm.register(name, callback) veya cm.registerFull(...) ile oluşturulan dinamik komut.
 * Callback: function(sender, args) şeklinde çağrılır.
 */
public class DynamicCommand extends Command {

    private final JSFunction callback;
    private final NanoEngine engine;
    private final Logger logger;

    public DynamicCommand(String name, String description, String usage,
                          JSFunction callback, NanoEngine engine, Logger logger) {
        super(name);
        this.callback = callback;
        this.engine   = engine;
        this.logger   = logger;
        setDescription(description);
        setUsage(usage);
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        try {
            JSValue jsSender = JSValue.wrap(sender);

            JSArray jsArgs = new JSArray();
            for (String arg : args) jsArgs.push(JSValue.of(arg));

            engine.call(callback, new JSValue[]{jsSender, JSValue.of(jsArgs)});

        } catch (JsError e) {
            sender.sendMessage("§c[NanoScript] Komut hatası: " + e.getMessage());
            logger.warning("[NanoScript] /" + getName() + " hatası: " + e.getMessage());
        } catch (Exception e) {
            sender.sendMessage("§c[NanoScript] Beklenmedik hata.");
            logger.severe("[NanoScript] /" + getName() + " beklenmedik hata: " + e.getMessage());
        }
        return true;
    }
}
