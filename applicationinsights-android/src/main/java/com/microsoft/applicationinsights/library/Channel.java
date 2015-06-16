package com.microsoft.applicationinsights.library;

import com.microsoft.applicationinsights.library.config.IQueueConfig;
import com.microsoft.applicationinsights.logging.InternalLogging;
import com.microsoft.telemetry.Base;
import com.microsoft.telemetry.Data;
import com.microsoft.telemetry.Domain;
import com.microsoft.telemetry.cs2.Envelope;
import com.microsoft.telemetry.IChannel;
import com.microsoft.telemetry.IJsonSerializable;

import java.util.Map;

/**
 * This class records telemetry for application insights.
 */
class Channel implements IChannel {
    private static final String TAG = "Channel";

    /**
     * Volatile boolean for double checked synchronize block
     */
    private static volatile boolean isChannelLoaded = false;

    /**
     * Synchronization LOCK for setting static context
     */
    private static final Object LOCK = new Object();

    /**
     * Test hook to the sender
     */
    protected ChannelQueue queue;

    /**
     * The singleton INSTANCE of this class
     */
    private static Channel instance;

    /**
     * Persistence used for saving unhandled exceptions.
     */
    private Persistence persistence;

    /**
     * Instantiates a new INSTANCE of Channel
     */
    protected Channel() {
        this.persistence = Persistence.getInstance();
    }

    protected static void initialize(IQueueConfig config) {
        // note: isPersistenceLoaded must be volatile for the double-checked LOCK to work
        if (!isChannelLoaded) {
            synchronized (Channel.LOCK) {
                if (!isChannelLoaded) {
                    isChannelLoaded = true;
                    instance = new Channel();
                    instance.setQueue(new ChannelQueue(config));
                }
            }
        }
    }

    /**
     * @return the INSTANCE of Channel or null if not yet initialized
     */
    protected static IChannel getInstance() {
        if (Channel.instance == null) {
            InternalLogging.error(TAG, "getInstance was called before initialization");
        }

        return Channel.instance;
    }

    /**
     * Persist all pending items.
     */
    public void synchronize() {
        this.queue.flush();
    }

    /**
     * Records the passed in data.
     *
     * @param data the base object to record
     */
    public void log(Base data, Map<String, String> tags) {
        if(data instanceof Data) {
            Envelope envelope = EnvelopeFactory.getInstance().createEnvelope((Data) data);

            // log to queue
            queue.enqueue(envelope);
            InternalLogging.info(TAG, "enqueued telemetry", envelope.getName());
        } else {
            InternalLogging.warn(TAG, "telemetry not enqueued, must be of type ITelemetry");
        }
    }

    protected void processUnhandledException(Data<Domain> data) {
        Envelope envelope = EnvelopeFactory.getInstance().createEnvelope(data);

        queue.isCrashing = true;
        queue.flush();

        IJsonSerializable[] rawData = new IJsonSerializable[1];
        rawData[0] = envelope;

        if (this.persistence != null) {
            this.persistence.persist(rawData, true);
        }
        else {
            InternalLogging.info(TAG, "error persisting crash", envelope.toString());
        }

    }

    /**
     * Test hook to set the queue for this channel
     *
     * @param queue the queue to use for this channel
     */
    protected void setQueue(ChannelQueue queue) {
        this.queue = queue;
    }

    /**
     * Set the persistence instance used to save unhandled exceptions.
     *
     * @param persistence the persitence instance which should be used
     */
    protected void setPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

}
