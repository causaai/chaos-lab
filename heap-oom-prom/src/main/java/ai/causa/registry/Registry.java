package ai.causa.registry;

import jakarta.enterprise.context.ApplicationScoped;
import ai.causa.model.TargetSamples;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
public class Registry {

    public final ConcurrentMap<String, TargetSamples> targets =
            new ConcurrentHashMap<>();

}