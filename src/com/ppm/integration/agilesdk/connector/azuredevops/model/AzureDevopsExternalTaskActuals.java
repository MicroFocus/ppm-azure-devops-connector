package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.ppm.integration.agilesdk.pm.ExternalTask;
import com.ppm.integration.agilesdk.pm.ExternalTaskActuals;

import java.util.Date;

/**
 * Fills all the gaps of actuals by automatically computing everything.
 */
public class AzureDevopsExternalTaskActuals extends ExternalTaskActuals {

    private Double effort;
    private ExternalTask.TaskStatus status;
    private long resourceId;
    private Date scheduledStart;
    private Date scheduledFinish;

    public AzureDevopsExternalTaskActuals(Double effort, ExternalTask.TaskStatus taskStatus, Date scheduledStart, Date scheduledFinish, Long resourceId) {
        this.effort = effort;
        this.status = status;
        this.resourceId = resourceId == null ? -1 : resourceId.longValue();
        this.scheduledFinish = scheduledFinish;
        this.scheduledStart = scheduledStart;
    }

    @Override
    public double getScheduledEffort() {
        return effort == null ? 0d: effort;
    }

    @Override
    public Date getActualStart() {
        if (this.status == ExternalTask.TaskStatus.READY) {
            return null;
        } else {
            return scheduledStart;
        }
    }

    @Override
    public Date getActualFinish() {
        if (this.status == ExternalTask.TaskStatus.COMPLETED) {
            return scheduledFinish;
        } else {
            return null;
        }
    }

    @Override
    public double getActualEffort() {
        return getPercentComplete() * getScheduledEffort();
    }

    @Override
    public double getPercentComplete() {
        if (status == ExternalTask.TaskStatus.COMPLETED) {
            return 100;
        } else if (status == ExternalTask.TaskStatus.IN_PROGRESS) {
            return 50;
        } else {
            return 0;
        }
    }

    @Override
    public long getResourceId() {
        return resourceId;
    }

    @Override
    public Double getEstimatedRemainingEffort() {
        // PPM enforces that PC = AE / (AE + ERE), so we have to compute ERE accordingly otherwise it will modify PC.
        if (getPercentComplete() <= 0) {
            return getScheduledEffort();
        } else {
            return getScheduledEffort() * (100 - getPercentComplete()) / getPercentComplete();
        }
    }
}
