package com.example.rosboxone.uwa.ros;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.HandlerThread;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.example.rosboxone.uwa.MainActivity;

import static android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.DefaultNodeMainExecutor;
import org.ros.node.Node;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMain;
import org.ros.node.NodeMainExecutor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;



/**
 * Created by rosboxone on 08/03/18.
 */

public class RosNodeConnection implements OnSharedPreferenceChangeListener
{

    private static RosNodeConnection rosInstance;
    private  static final String TAG = RosNodeConnection.class.getName();


    private final HandlerThread mThread;
    private final Handler mHandler;
    private final String mAddress;
    private final String mPort;

    private final List<NodeMain> mNodes = new ArrayList<>();

    private NodeMainExecutor nodeMainExecutor;
    private URI masterURI;


    // Return Instance of ROS Connection
    public static RosNodeConnection getRosNodeInstance()
    {
        if(rosInstance == null)
        {
            rosInstance = new RosNodeConnection();
        }

        return  rosInstance;
    }


    RosNodeConnection()
    {
        // Get the PreferenceScreen Keys
        Context context = MainActivity.getInstance().getApplicationContext();
        mAddress = "address";
        mPort = "port";

        mThread = new HandlerThread("ros");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        masterURI = getURISettings();
    }


    // Generate Master URI for ROS COnnection
    private URI getURISettings()
    {
        //Return Shared Preferences from Main Activity
        MainActivity main = MainActivity.getInstance();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(main);

        String address = sharedPref.getString(mAddress, "131.231.187.145");
        int port = sharedPref.getInt(mPort, 11311);

        URI rosURI = URI.create("http://" + String.valueOf(address) + ':' + String.valueOf(port)+ '/');

        return  rosURI;
    }

    /**
     * Launch a ros node and connect to the master node
     *
     * parameters NODE TO LAUNCH
     *            Using an anonymous class implementation
     *
     */

    public  void  launchNode(NodeMain node)
    {
        mHandler.post(new LaunchNode(node));

    }

    /**
     * Shutdown individual ROS Nodes
     * parameters  Node to shudown
     */

    public void shutdownNode(NodeMain node)
    {
        mHandler.post(new ShutdownNode(node)) ;
    }


    /**
     * Shutdown all ROS Nodes. Call before exiting application. (onDestroy).
     * @parameters None
     */

    public void shutdown()
    {
        mHandler.post(new Shutdown()) ;
    }


    /**
     * Restarts ROS Nodes launched by the launcher. It's used when URI to master node changes
     * @parameters  None
     */

    public void restart()
    {
        mHandler.post(new Restart()) ;
    }



    // Anonymous classes implementation using the Runnable interface
    //Adapted from the ROS NODE Sample online and MRASL Android App for the DJI Dev 2016 Challenge
    private class LaunchNode implements  Runnable
    {
        private final NodeMain mNode;
        public LaunchNode(NodeMain node)
        {
            mNode = node;


        }
        @Override
        public void run() {

            if(nodeMainExecutor == null)
            {
                nodeMainExecutor = DefaultNodeMainExecutor.newDefault();
            }

            // Connect to the Master URI
            NodeConfiguration nodeConfiguration = NodeConfiguration.newPrivate(masterURI);

            // Start the ROS NOde
            nodeMainExecutor.execute(mNode, nodeConfiguration);

            if(!mNodes.contains(mNode))
            {
                mNodes.add(mNode);
            }

        }
    }


    private class Shutdown implements Runnable
    {
        @Override
        public void run()
        {
            //Shutdown node
            nodeMainExecutor.shutdown();
            nodeMainExecutor = null;


            mNodes.clear();


        }

    }

    private class ShutdownNode implements Runnable
    {
        private  final NodeMain aNode;
        public ShutdownNode(NodeMain node)
        {
            aNode = node;

        }

        @Override
        public void run() {

            if (!mNodes.contains(aNode))
            {
                return;

            }
            // Shutdown the node
            nodeMainExecutor.shutdownNodeMain(aNode);

            mNodes.remove(aNode);

        }


    }

    private class Restart implements Runnable
    {

        @Override
        public void run()
        {
            // Shutdown all nodes
            nodeMainExecutor.shutdown();
            nodeMainExecutor = null;

            for(NodeMain node : mNodes)
            {
                launchNode(node);
            }
        }


    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key)
    {
        // Check the address or port that was changed;
        if(key!= mAddress && key != mPort)
        {
            return;
        }

        // Recreate the URI from master node
        masterURI = getURISettings();

        restart();

    }
}
