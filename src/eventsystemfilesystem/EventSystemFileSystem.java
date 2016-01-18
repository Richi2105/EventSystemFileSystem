/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package eventsystemfilesystem;

import displayLib.DotMatrixClient;
import displayLib.string.DisplayList;
import displayLib.string.DisplayString;
import eventSystem.Constants;
import eventSystem.input.Key;
import eventSystem.logging.Log;
import eventSystem.logging.LoggerAdapter;
import eventSystem.participants.EventSystemClient;
import eventSystem.telegram.Telegram;
import eventSystem.telegram.TelegramObject;
import java.io.File;
import displayLib.DisplayCommunication;
import tokenDaemon.Behavior_Object;
import tokenDaemon.Function_Description;
import tokenDaemon.Function_List;
import tokenDaemon.Token_Object;

/**
 *
 * @author richard
 */
public class EventSystemFileSystem {
    
    private EventSystemClient client;
    private DotMatrixClient dmclient;
    private DisplayList fileList;
    private String uid_menu;
    private File currentFolder;
    
    private Behavior_Object behavior;
    
    public EventSystemFileSystem(String startPath)
    {
        this.client = new EventSystemClient("FILESYSTEM");
        this.dmclient = new DotMatrixClient();
        
        TelegramObject keyTelegram = new TelegramObject();
        TelegramObject tokenTelegram = new TelegramObject();
        Key pressedKey = new Key();
        keyTelegram.setObject(pressedKey);
        
        Telegram telegram = new Telegram();
        
        DisplayString.setCommunicationModule(dmclient);
        DisplayList.setCommunicationModule(dmclient);
        
        //DisplayString str = new DisplayString(0, 0, 122, "Test");
        //str.display();
        this.client.connectToMaster();
        this.client.startReceiving();
        LoggerAdapter.initLoggerAdapter(client);
        
        currentFolder = new File(startPath);
        this.fileList = new DisplayList(DisplayCommunication.BOUNDARY_FULL, DisplayCommunication.SIDE_RIGHT);
        
        this.getFiles(currentFolder);
        
        this.behavior = new Behavior_Object();
        
        //this.showFiles(currentFolder);
        
        byte[] data = new byte[Constants.DATASIZE];
        while (true)
        {
            this.client.receive(data, false);            
            telegram.deserialize(data);
            System.out.println(String.format("received data of kind %d", telegram.getType()));
            LoggerAdapter.log(Log.LOG_INFO, String.format("received data of kind %d", telegram.getType()));
            
            if (telegram.getType() == Telegram.INPUT)
            {
                keyTelegram.deserialize(data, pressedKey);
                if (this.navigate(pressedKey) != 0)
                {
                    switch (pressedKey.getKeyIdentifier()) {
                        case Key.KEY_STOP: {
                            this.cancel();
                            break;
                        }
                    }
                }
                LoggerAdapter.log(Log.LOG_INFO, String.format("received key: %s", pressedKey.getKeyDescription()));
            }
            else if (telegram.getType() == Telegram.REQUEST)
            {
                char[] source = new char[Constants.UNIQUEID_SIZE];
                int i = 0;
                for(byte b : telegram.getSourceID())
                {
                    source[i++] = (char) b;
                }
                uid_menu = new String(source);
                this.sendFunctions(uid_menu);
            }
            else if (telegram.getType() == Telegram.TOKEN_NEXT)
            {
                TelegramObject funcTelegram = new TelegramObject();
                Token_Object to = new Token_Object();
                funcTelegram.deserialize(data, to);
                System.out.println(String.format("Function to be called: %s", to.getFunction().getFunctionDescription().toString()));
                this.fileList.display();
            }
            else if (telegram.getType() == Behavior_Object.BEHAVIOR)
            {
                TelegramObject behaviorTelegram = new TelegramObject();
                behaviorTelegram.deserialize(data, this.behavior);
                LoggerAdapter.log(Log.LOG_INFO, "Some behavior received");
            }
            else
            {
                LoggerAdapter.log(Log.LOG_WARNING, "Wrong telegram received");
            }
            
            
        }
        
    }
    
    private void getFiles(File parent)
    {
        this.fileList.clear();
        for (File subEntry : parent.listFiles())
        {
            this.fileList.addEntry(subEntry.getName());
            System.out.println(subEntry.getPath());
        }
    }
    
    private int navigate(Key pressedKey)
    {
        switch (pressedKey.getKeyIdentifier()) {
        case Key.KEY_KNOB_UP: {
            fileList.scrollUp(1);
            this.fileList.display();
            return 0;
        } 
        case Key.KEY_KNOB_DOWN: {
            fileList.scrollDown(1);
            this.fileList.display();
            return 0;
        }
        case Key.KEY_ENTER: {
            currentFolder = new File(currentFolder, fileList.getEntryAt(fileList.getSelectedEntry()));
            fileList.clear();
            this.getFiles(currentFolder);
            this.fileList.display();
            return 0;
        }
        case Key.KEY_PREVIOUS: {
            currentFolder = new File(currentFolder.getParent());
            fileList.clear();
            this.getFiles(currentFolder);
            this.fileList.display();
            return 0;
        }
        default: return -1;
        }
    }
    
    private void cancel()
    {
        Token_Object to = new Token_Object(uid_menu, this.behavior.getFunc_onCancel());
        TelegramObject tokenTelegram = new TelegramObject("INPUTRECEIVER", to);
        tokenTelegram.setType(Token_Object.TOKEN_NEXT);
        this.client.send(tokenTelegram);
    }
    
    private void success()
    {
        Token_Object to = new Token_Object(uid_menu, this.behavior.getFunc_onSuccess());
        TelegramObject tokenTelegram = new TelegramObject("INPUTRECEIVER", to);
        tokenTelegram.setType(Token_Object.TOKEN_NEXT);
        this.client.send(tokenTelegram);
    }
    
    private void sendFunctions(String toUID)
    {
        Function_Description[] descriptions = new Function_Description[2];
        descriptions[0] = new Function_Description("displayHome");
        descriptions[1] = new Function_Description("displayRoot");
        
        Function_List list = new Function_List(this.client.getUniqueIdentifier(), (byte)2, descriptions);
        TelegramObject functionListTelegram = new TelegramObject(toUID, list);
        functionListTelegram.setType(Function_List.REQUEST_ANSWER);
        this.client.send(functionListTelegram);
        
        LoggerAdapter.log(Log.LOG_INFO, "Sending info to " + toUID);
    }
        

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0)
            System.out.println("Usage: a valid path (try $HOME)");
        else
            new EventSystemFileSystem(args[0]);
    }
    
}
