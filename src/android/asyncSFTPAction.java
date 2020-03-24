/*
 * The MIT License (MIT)
 * Copyright (c) 2015 Joel De La Torriente - jjdltc - https://github.com/jjdltc
 * See a full copy of license in the root folder of the project
 */
package com.jjdltc.cordova.plugin.sftp;

import java.io.*;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.util.Calendar;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.cordova.CordovaWebView;

public class asyncSFTPAction extends AsyncTask<Void, Integer, Boolean> {

    private JSch jsch               = null;
    private Session session         = null;
    private ChannelSftp sftpChannel = null;
    
    private JSONObject hostData     = null;
    private JSONArray actionArr     = null;
    private CordovaWebView actualWv = null;
    private String action           = "";
    private int fileListSize        = 0;
    private boolean actionExecutionSuccessfull  = true;
    private boolean doConnectionSuccessfull  = true;
    
    public asyncSFTPAction(JSONObject hostData, JSONArray actionArr, String action, CordovaWebView actualWv) {
        this.hostData   = hostData;
        this.actionArr  = actionArr;
        this.actualWv   = actualWv;
        this.action     = action;
    }
    
    @Override
    protected Boolean doInBackground(Void... params) {
        boolean result = true;
        try {
           this.costumLog("iad-SFTP", "doConnection... ");
            this.doConnection(this.hostData);
           this.costumLog("iad-SFTP", "doConnectionSuccessfull "+ this.doConnectionSuccessfull);
            
            if(this.doConnectionSuccessfull){
               this.costumLog("iad-SFTP", "actionExecution... ");
                this.actionExecution(this.actionArr);
               this.costumLog("iad-SFTP", "actionExecutionSuccessfull "+ this.actionExecutionSuccessfull);
                this.closeConn();
            }

        } catch (Exception e) { /*  JSchException | SftpException e */
            e.printStackTrace();
             this.costumLog("iad-SFTP - ERROR", e.toString()); 
            this.costumLog("SFTP Plugin", "There was a problem in the async execution" );
            this.closeConn();
            result = false;
        }
       this.costumLog("iad-SFTP", "result.. "+ result);
       this.costumLog("iad-SFTP", "actionExecutionSuccessfull.. "+ this.actionExecutionSuccessfull);
       this.costumLog("iad-SFTP", "doConnectionSuccessfull.. "+ this.doConnectionSuccessfull);
        result = result && this.actionExecutionSuccessfull && this.doConnectionSuccessfull;
       this.costumLog("iad-SFTP", "latest result "+ result);
        return result;
    }
    
    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        this.jsEvent("SFTPActionListProgress", "{progress:'"+progress[0]+"', total:'"+this.fileListSize+"'}");
       this.costumLog("SFTP Plugin", "File progress: "+progress[0]+" of "+this.fileListSize+" Complete" );
    }
    
    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        this.jsEvent("SFTPActionListEnd", "{all:'"+result+"'}");
       this.costumLog("SFTP Plugin", "All the files "+((result)?"were":"weren't")+" reach it" );
    }
    
    @Override
    protected void onCancelled() {
        super.onCancelled();
        this.closeConn();
        this.jsEvent("SFTPActionCancell", null);
       this.costumLog("SFTP Plugin", "Action cancelled by the user" );
    }
    
    @SuppressWarnings("static-access")
    private void doConnection(JSONObject hostData) throws JSchException{
        this.doConnectionSuccessfull = true;
        try{        
            this.jsch = new JSch();
            this.jsch.setConfig("StrictHostKeyChecking", "no");
            
            this.session = jsch.getSession(hostData.optString("user"), hostData.optString("host"), hostData.optInt("port"));
            this.session.setPassword(hostData.optString("pswd"));
            this.session.connect(); 
            this.sftpChannel = (ChannelSftp) session.openChannel("sftp");
            this.sftpChannel.connect();
           this.costumLog("SFTP Plugin", "Connection Open.");
            this.jsEvent("SFTPActionConnected", null);

        } catch (Exception e) { /*  JSchException | SftpException e */
       this.costumLog("iad-SFTP", "doConnectionSuccessfull was unsuccessfull.");
        this.doConnectionSuccessfull = false;
        e.printStackTrace();
        this.costumLog("iad-SFTP - ERROR", e.toString()); 
        }
    }

    private void actionExecution(JSONArray actions) throws SftpException{
        this.actionExecutionSuccessfull = true;
        this.fileListSize = actions.length();
        for (int i = 0; i < actions.length(); i++) {
            JSONObject item = (JSONObject)actions.opt(i);
            boolean isDownload              = (action == "download")?true:false;
            boolean createIfNeedIt          = item.optBoolean("create");
            boolean isValidLocalPath        = this.checkLocalPath(item.optString("local"), isDownload, createIfNeedIt);
            if(isValidLocalPath){
                if(isDownload){
                    this.costumLog("iad-SFTP", "Download.");
                     try{
                        this.sftpChannel.get(item.optString("remote"), item.optString("local"), new progressMonitor(this.actualWv));                
                     }
                     catch(Exception e) { 
                        this.costumLog("iad-SFTP", "actionExecutionSuccessfull was unsuccessfull.");
                        this.actionExecutionSuccessfull = false;
                         e.printStackTrace();                        
                        this.costumLog("iad-SFTP - ERROR", e.toString()); 
                        }                   
                    
                }
                else{
                    this.sftpChannel.put(item.optString("local"), item.optString("remote"), new progressMonitor(this.actualWv), ChannelSftp.OVERWRITE);
                }
            }
            this.publishProgress(i+1);
        }
    }

    private boolean checkLocalPath(String path, boolean isDownload, boolean create){
        String pathToSeek   = (isDownload)?path.substring(0, path.lastIndexOf("/")+1):path;
        File seekedPath     = new File(pathToSeek);
        boolean Exists      = seekedPath.exists();
        if(!Exists && create && isDownload){
            seekedPath.mkdirs(); 
            Exists          = seekedPath.exists();
        }
        return Exists;
    }

    private void closeConn(){
        this.sftpChannel.exit();
        this.session.disconnect();
        this.jsEvent("SFTPActionDisconnected", null);
       this.costumLog("SFTP Plugin", "Connection Close.");
    }
    
    @SuppressWarnings("deprecation")
    private void jsEvent(String evt, String data){
        String eventString = "cordova.fireDocumentEvent('"+evt+"'";
        if(data != null && !data.isEmpty()){
            eventString += ", "+data;
        }
        eventString += ");";
       //this.costumLog("JJDLTC JS TEST", eventString);
        this.actualWv.sendJavascript(eventString);
    }    



    public void costumLog(String plugin,String text)
    {       
        Date currentTime = Calendar.getInstance().getTime();
        Log.d(plugin, text);
        File logFile = new File("sdcard/IAD-LOGS/IAD-native.log");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            } 
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true)); 
            buf.append( currentTime.toString()+": " + plugin+":    "+text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
