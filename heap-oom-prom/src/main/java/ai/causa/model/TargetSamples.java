package ai.causa.model;

import java.util.List;

public class TargetSamples {

    public ContainerTarget target;
    public List<MetricSample> samples;

    public TargetSamples(ContainerTarget target, List<MetricSample> samples) {
        this.target = target;
        this.samples = samples;
    }
}