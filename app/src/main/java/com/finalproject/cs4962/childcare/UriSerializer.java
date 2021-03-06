package com.finalproject.cs4962.childcare;

import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Created by akdPro on 12/20/14.
 */
public class UriSerializer implements JsonSerializer<Uri> {

    public UriSerializer(){

    }

    public JsonElement serialize(Uri src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}