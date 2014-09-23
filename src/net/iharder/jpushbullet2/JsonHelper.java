/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package net.iharder.jpushbullet2;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Type;

/**
 *
 * @author rob
 */
public class JsonHelper {
    
    /**
     * Thanks, Google, for the easy JSON reading.
     */
    private static Gson gson = new Gson();
    
    
    
    /**
     * Convert json text to an object using whatever json converter is handy.
     * This helps abstract the json-specific converter from the rest of the
     * code. For example this method call would stand in for the following
     * Gson call:
     * <code><pre>PushList pushList = gson.fromJson(msg, PushList.class);</pre></code>
     * 
     * @param <T> The class of the object to convert into
     * @param jsonText the json text
     * @param typeOfT The class of the object to convert into
     * @return the object converted from json
     * @throws PushbulletException if there is an error
     */
    public static <T extends Object> T fromJson(String jsonText, Type typeOfT) throws PushbulletException {
        try{
            return gson.fromJson( jsonText, typeOfT );
        } catch( JsonIOException | JsonSyntaxException ex ){
            throw new PushbulletException( ex );
        }
    }
    
}
