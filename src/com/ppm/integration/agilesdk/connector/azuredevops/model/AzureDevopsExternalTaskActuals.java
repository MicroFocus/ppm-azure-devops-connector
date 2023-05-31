package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.ppm.integration.agilesdk.pm.ExternalTask;
import com.ppm.integration.agilesdk.pm.ExternalTaskActuals;

import java.util.Date;

/**
 * Fills all the gaps of actuals by automatically computing everything.
 */
public class AzureDevopsExternalTaskActuals extends ExternalTaskActuals {

    private Double scheduledEffort;

    private Double remainingEffort;

    private Double actualEffort;

    private double percentComplete;
    private ExternalTask.TaskStatus status;
    private long resourceId;
    private Date scheduledStart;
    private Date scheduledFinish;

    public AzureDevopsExternalTaskActuals(Double scheduledEffort, Double remainingEffort, ExternalTask.TaskStatus taskStatus, Date scheduledStart, Date scheduledFinish, Long resourceId) {
        this.scheduledEffort = scheduledEffort == null ? 0d: scheduledEffort;
        this.remainingEffort = remainingEffort == null ? 0d : remainingEffort;
        this.status = taskStatus;
        this.resourceId = resourceId == null ? -1 : resourceId.longValue();
        this.scheduledFinish = scheduledFinish;
        this.scheduledStart = scheduledStart;

        // We will now adjust effort / remaining effort / percent complete to ensure that they match the standard PPM formulas:
        // PC = AE / (AE + ERE)
        // AE + ERE = SE
        // AE = SE * PC
        if (status == ExternalTask.TaskStatus.COMPLETED) {
            // Completed -> 100% complete, no remaining effort.
            percentComplete = 100;
            this.remainingEffort = 0d;
            this.actualEffort = this.scheduledEffort;
        } else if (status == ExternalTask.TaskStatus.IN_PROGRESS) {
            // If we have both scheduled & remaining effort, we can compute percent complete.
            if (remainingEffort != null && scheduledEffort != null) {
                this.actualEffort = scheduledEffort - remainingEffort;
                if (this.actualEffort < 0) {
                    this.actualEffort = 0d;
                }
                if (this.actualEffort > 0d) {
                    this.percentComplete = 100d * this.actualEffort / (this.actualEffort + this.remainingEffort);
                    if (this.percentComplete > 99d) {
                        this.percentComplete = 99d;
                    }
                } else {
                    // No actuals -> 1% complete
                    this.percentComplete = 1d;
                }
            } else if (remainingEffort != null) {
                // We only know how much work remains, so let's assume we're mid-way.
                this.percentComplete = 50d;
                this.scheduledEffort = this.remainingEffort * 2;
                this.actualEffort = remainingEffort;
            } else if (scheduledEffort != null) {
                // We only know what's the total effort - so let's assume we're mid-way.
                this.percentComplete = 50d;
                this.actualEffort = this.scheduledEffort / 2d;
                this.remainingEffort = this.scheduledEffort / 2d;
            } else {
                // We know nothing. We're still mid-way.
                this.percentComplete = 50d;
                this.actualEffort = 0d;
            }
        } else {
            // Work not started.
            percentComplete = 0;
            this.actualEffort = 0d;
            if (remainingEffort == null) {
                this.remainingEffort = this.scheduledEffort;
            } else if (scheduledEffort == null) {
                this.scheduledEffort = this.remainingEffort;
            }
        }

    }

    @Override
    public double getScheduledEffort() {
        return scheduledEffort;
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
        return this.actualEffort;
    }

    @Override
    public double getPercentComplete() {
        return percentComplete;
    }

    @Override
    public long getResourceId() {
        return resourceId;
    }

    @Override
    public Double getEstimatedRemainingEffort() {
        return this.remainingEffort;
    }
}
