package ch.qos.logback.core.rolling;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.rolling.aws.AmazonS3ClientImpl;
import ch.qos.logback.core.rolling.shutdown.RollingPolicyShutdownListener;
import ch.qos.logback.core.rolling.shutdown.ShutdownHookType;
import ch.qos.logback.core.rolling.shutdown.ShutdownHookUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * User: gvhoecke <gianni.vanhoecke@lin-k.net>
 * Date: 13/07/15
 * Time: 16:53
 */
public class S3TimeBasedRollingPolicy<E> extends TimeBasedRollingPolicy<E> implements RollingPolicyShutdownListener {

    private String awsAccessKey;
    private String awsSecretKey;
    private String s3BucketName;
    private String s3FolderName;
    private boolean rolloverOnExit;
    private ShutdownHookType shutdownHookType;

    private AmazonS3ClientImpl s3Client;
    private ExecutorService executor;

    public S3TimeBasedRollingPolicy() {

        super();

        rolloverOnExit = false;
        shutdownHookType = ShutdownHookType.NONE;

        executor = Executors.newFixedThreadPool( 1 );
    }

    @Override
    public void start() {

        super.start();

        //Init S3 client
        s3Client = new AmazonS3ClientImpl( getAwsAccessKey(), getAwsSecretKey(), getS3BucketName(), getS3FolderName() );

        //Register shutdown hook so the log gets uploaded on shutdown, if needed
        ShutdownHookUtil.registerShutdownHook( this, getShutdownHookType() );
    }

    @Override
    public void rollover()
            throws RolloverFailure {

        final String elapsedPeriodsFileName =
                String.format(
                        "%s%s",
                        timeBasedFileNamingAndTriggeringPolicy.getElapsedPeriodsFileName(),
                        getFileNameSuffix() );

        super.rollover();

        //Queue upload the current log file into S3
        //Because we need to wait for the file to be rolled over, use a thread so this doesn't block.
        executor.execute( new UploadQueuer( elapsedPeriodsFileName ) );
    }

    /**
     * Shutdown hook that gets called when exiting the application.
     */
    @Override
    public void doShutdown() {

        if( isRolloverOnExit() ) {

            //Do rolling and upload the rolled file on exit
            rollover();
        }
        else {

            //Upload the active log file without rolling
            s3Client.uploadFileToS3Async( getActiveFileName() );
        }

        //Shutdown executor
        try {

            executor.shutdown();
            executor.awaitTermination( 10, TimeUnit.MINUTES );
        }
        catch( InterruptedException e ) {

            executor.shutdownNow();
        }

        //Wait until finishing the upload
        s3Client.doShutdown();
    }

    private void waitForAsynchronousJobToStop() {

        if( future != null ) {

            try {

                future.get( CoreConstants.SECONDS_TO_WAIT_FOR_COMPRESSION_JOBS, TimeUnit.SECONDS);
            }
            catch( TimeoutException e ) {

                addError( "Timeout while waiting for compression job to finish", e );
            }
            catch( Exception e ) {

                addError( "Unexpected exception while waiting for compression job to finish", e );
            }
        }
    }

    private String getFileNameSuffix() {

        switch( compressionMode ) {

            case GZ:

                return ".gz";

            case ZIP:

                return ".zip";

            case NONE:
            default:

                return "";
        }
    }

    class UploadQueuer implements Runnable {

        private final String elapsedPeriodsFileName;

        public UploadQueuer( final String elapsedPeriodsFileName ) {

            this.elapsedPeriodsFileName = elapsedPeriodsFileName;
        }

        @Override
        public void run() {

            try {

                waitForAsynchronousJobToStop();
                s3Client.uploadFileToS3Async( elapsedPeriodsFileName );
            }
            catch( Exception ex ) {

                ex.printStackTrace();
            }
        }
    }

    public String getAwsAccessKey() {

        return awsAccessKey;
    }

    public void setAwsAccessKey( String awsAccessKey ) {

        this.awsAccessKey = awsAccessKey;
    }

    public String getAwsSecretKey() {

        return awsSecretKey;
    }

    public void setAwsSecretKey( String awsSecretKey ) {

        this.awsSecretKey = awsSecretKey;
    }

    public String getS3BucketName() {

        return s3BucketName;
    }

    public void setS3BucketName( String s3BucketName ) {

        this.s3BucketName = s3BucketName;
    }

    public String getS3FolderName() {

        return s3FolderName;
    }

    public void setS3FolderName( String s3FolderName ) {

        this.s3FolderName = s3FolderName;
    }

    public boolean isRolloverOnExit() {

        return rolloverOnExit;
    }

    public void setRolloverOnExit( boolean rolloverOnExit ) {

        this.rolloverOnExit = rolloverOnExit;
    }

    public ShutdownHookType getShutdownHookType() {

        return shutdownHookType;
    }

    public void setShutdownHookType( ShutdownHookType shutdownHookType ) {

        this.shutdownHookType = shutdownHookType;
    }
}
