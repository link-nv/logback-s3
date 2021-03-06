package ch.qos.logback.core.rolling.aws;

import ch.qos.logback.core.rolling.shutdown.RollingPolicyShutdownListener;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * User: gvhoecke <gianni.vanhoecke@lin-k.net>
 * Date: 14/07/15
 * Time: 08:28
 */
public class AmazonS3ClientImpl implements RollingPolicyShutdownListener {

    private final String awsAccessKey;
    private final String awsSecretKey;
    private final String s3BucketName;
    private final String s3FolderName;

    private AmazonS3Client amazonS3Client;
    private ExecutorService executor;

    public AmazonS3ClientImpl( String awsAccessKey,
                               String awsSecretKey,
                               String s3BucketName,
                               String s3FolderName ) {

        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.s3BucketName = s3BucketName;
        this.s3FolderName = s3FolderName;

        executor = Executors.newFixedThreadPool( 1 );
        amazonS3Client = null;
    }

    public void uploadFileToS3Async( final String filename ) {

        if( amazonS3Client == null ) {

            amazonS3Client = new AmazonS3Client( new BasicAWSCredentials( getAwsAccessKey(), getAwsSecretKey() ) );
        }

        final File file = new File( filename );

        //If file does not exist or if empty, do nothing
        if( !file.exists() || file.length() == 0 ) {

            return;
        }

        //Build S3 path
        final StringBuffer s3ObjectName = new StringBuffer();
        if( getS3FolderName() != null ) {

            s3ObjectName.append( getS3FolderName() ).append( "/" );
        }

        s3ObjectName.append( new SimpleDateFormat( "yyyyMMdd_HHmmss" ).format( new Date() ) ).append( "_" );
        s3ObjectName.append( file.getName() );

        //Queue thread to upload
        Runnable uploader = new Runnable() {

            @Override
            public void run() {

                try {

                    amazonS3Client.putObject( getS3BucketName(), s3ObjectName.toString(), file );
                }
                catch( Exception ex ) {

                    ex.printStackTrace();
                }
            }
        };

        executor.execute( uploader );
    }

    /**
     * Shutdown hook that gets called when exiting the application.
     */
    @Override
    public void doShutdown() {

        try {

            //Wait until finishing the upload
            executor.shutdown();
            executor.awaitTermination( 10, TimeUnit.MINUTES );
        }
        catch( InterruptedException e ) {

            executor.shutdownNow();
        }
    }

    public String getAwsAccessKey() {

        return awsAccessKey;
    }

    public String getAwsSecretKey() {

        return awsSecretKey;
    }

    public String getS3BucketName() {

        return s3BucketName;
    }

    public String getS3FolderName() {

        return s3FolderName;
    }
}
