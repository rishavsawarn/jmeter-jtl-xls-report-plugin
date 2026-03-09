package com.example.jmeter.plugin;

import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestStateListener;

public class JtlToXlsListener extends AbstractTestElement
        implements SampleListener, TestStateListener {

    @Override
    public void testStarted() {}

    @Override
    public void testStarted(String host) {}

    @Override
    public void testEnded() {

        try {

            String jtlDirectory = getPropertyAsString("jtlDirectory");
            String outputDirectory = getPropertyAsString("outputDirectory");
            String environment = getPropertyAsString("environment");
            String url = getPropertyAsString("url");
            String outputFileName = getPropertyAsString("outputFileName");
            String transactionLabel = getPropertyAsString("transactionLabel");
            String scriptingName = getPropertyAsString("scriptingName");

            // NEW: Read SLA value
            double slaValue = Double.parseDouble(
                    getPropertyAsString("slaValue", "10")
            );

            new JMeterAggregateFull().generateReport(
                    jtlDirectory,
                    outputDirectory,
                    environment,
                    url,
                    outputFileName,
                    transactionLabel,
                    scriptingName,
                    slaValue
            );

            System.out.println("Excel report generated successfully");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void testEnded(String host) {
        testEnded();
    }

    @Override
    public void sampleOccurred(SampleEvent event) {}

    @Override
    public void sampleStarted(SampleEvent event) {}

    @Override
    public void sampleStopped(SampleEvent event) {}
}