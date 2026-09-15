package com.emreuzum.argame.cloud;

public class CloudCapture {

    public String captureId;
    public int creatureId;
    public String creatureName;
    public long capturedAtEpochMs;

    public CloudCapture(){

    }

    public CloudCapture(String captureId, int creatureId, String creatureName, long capturedAtEpochMs){
        this.captureId = captureId;
        this.creatureId = creatureId;
        this.creatureName = creatureName;
        this.capturedAtEpochMs = capturedAtEpochMs;
    }
}
