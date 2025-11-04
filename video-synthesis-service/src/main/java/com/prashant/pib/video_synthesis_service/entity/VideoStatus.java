package com.prashant.pib.video_synthesis_service.entity;

/**
 * Represents the lifecycle of a video generation job.
 */
public enum VideoStatus {
    /**
     * The job has been created but not yet submitted to the Python service. (Future use)
     */
    PENDING,
    /**
     * The job has been submitted and is currently being processed by Python.
     */
    PROCESSING,
    /**
     * The video has been successfully generated and is available for viewing.
     */
    COMPLETED,
    /**
     * An error occurred during the generation process.
     */
    FAILED,
    /**
     * FLAW FIX: The video has been explicitly published by an admin and is now publicly accessible.
     */
    PUBLISHED
}

