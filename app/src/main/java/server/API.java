package server;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import model.Address;
import model.Availability;
import model.Child;
import model.Event;
import model.Friend;
import model.Person;

/**
 * Created by Braden on 11/29/2014.
 */
public class API {

    private String baseUrl = "http://childcareappservice.azurewebsites.net/api/";
    //private String baseUrl = "http://10.0.0.5:5142/api/"; //THIS STRING IS FOR LOCAL TESTING
    private String peopleUrl = "people";
    private String personUrl = "people/{id}";
    private String eventsUrl = "event";
    private String childrenUrl = "children";
    private String addressUrl = "addresses";
    private String addressidUrl = "addresses/{id}";
    private String friendsUrl = "friends/";
    private static API singleton = null;
    private static boolean finishedFirstPeopleLoad = false;
    ///////////////////////////////////////////////////////////////////////////////
    public static API Get() {
        if(singleton == null) {
            singleton = new API();
            return singleton;
        }

//if it's not the first grab make sure it's loaded at least once
        try {
            while (!finishedFirstPeopleLoad) Thread.sleep(1000);
        } catch(Exception e) { e.printStackTrace(); }
        API copy = null;
        try { //thread safe singleton
            singleton.lock.lock();
            copy = (API)singleton;
            singleton.lock.unlock();
        } catch(Exception e) { e.printStackTrace(); }

        return copy;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private API() {
        //really nothing except load the people
        final API api = this;
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                peopleLock.lock();
                GetPeople();
                peopleLock.unlock();

                finishedFirstPeopleLoad = true;
            }
        }, 0, 50000);
    }
    ///////////////////////////////////////////////////////////////////////////////
    private void addPersonToCache(Person person) {
        peopleLock.lock();
        List<Person> list = new ArrayList<Person>(Arrays.asList(people));
        list.add(person);
        Person[] ret = new Person[list.size()];
        for(int i = 0; i < list.size(); i++) {
            ret[i] = list.get(i);
        }
        people = ret.clone();
        peopleLock.unlock();
    }
    ///////////////////////////////////////////////////////////////////////////////
    public void AddEvent(Event event) {
        try {
            PerformQuery(baseUrl + friendsUrl, new JSONObject(new Gson().toJson(event))); //should block until finished
        } catch(Exception e ) { e.printStackTrace(); }

        response = null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public void AddChildToParent(Person child, Person parent) {
        try {
            child.availability = parent.availability;
            child.address = parent.address;

            AddPerson(child, child.address, null, child.availability); //the person must be in before we add the child

            PerformQuery(baseUrl + childrenUrl, new JSONObject(new Gson().toJson(child))); //add the child
        } catch(Exception e) { e.printStackTrace(); }

        response = null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public void AddFriend(Person person1, Person person2) {
        if(GetPeopleCached() == null)
            GetPeople();

        for(Person p : GetPeopleCached()) {
            if(p.firstname.equals(person1.firstname) && p.lastname.equals(person1.lastname))
                person1.idperson = p.idperson;
            if(p.firstname.equals(person2.firstname) && p.lastname.equals(person2.lastname))
                person2.idperson = p.idperson;

        }
        Friend f = new Friend();
        f.Person1 = person1.idperson;
        f.Person2 = person2.idperson;
        try {
            PerformQuery(baseUrl + friendsUrl, new JSONObject(new Gson().toJson(f)));
        } catch(Exception e) { e.printStackTrace(); }
        response = null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Person[] GetFriends(Person person) {
        PerformQuery(baseUrl + friendsUrl, null); //should block until finished
        Friend[] people = new Gson().fromJson(response.toString(), Friend[].class);
        response = null;
        ArrayList<Person> friends = new ArrayList<Person>();
        for(Friend f : people) {
            if(f.Person1 == person.idperson) { //this should speed things up quite a bit
                friends.add(GetPersonFromSavedPeople(f.Person2));
            } else if(f.Person2 == person.idperson) {
                friends.add(GetPersonFromSavedPeople(f.Person1));
            }
        }
        Person[] ret = new Person[friends.size()];
        for(int i = 0; i < friends.size(); i++) {
            ret[i] = friends.get(i);
        }

        return ret;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private Person GetPersonFromSavedPeople(int id) {
        if(GetPeopleCached() == null) GetPeople();

        for(Person p : GetPeopleCached()) {
            if(p.idperson == id) return p;
        }

        return null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private void SetPeopleCached(Person[] people) {
        peopleLock.lock();
        this.people = people;
        peopleLock.unlock();
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Person[] GetPeopleCached() {

        if(people == null) return null;
        peopleLock.lock();
        Person[] copy = people.clone();
        peopleLock.unlock();
        return copy;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private Person[] GetPeople() {
        PerformQuery(baseUrl + peopleUrl, null); //should block until finished
        List<Person> people = new ArrayList<Person>();
        JSONArray arr = (JSONArray)response; //i know it's an array
        GsonBuilder builder = new GsonBuilder();
        builder.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE);
        Gson gson = builder.create();

        for(int i = 0; i < arr.length(); i++) {
            try {
                Person p = new Person();
                JSONObject obj = arr.getJSONObject(i);
                p.firstname =  obj.getString("FirstName");
                p.lastname =  obj.getString("LastName");
                p.phonenumber = obj.getString("PhoneNumber");
                p.idperson = obj.getInt("ID");

                p.address = GetAddress(obj.getInt("AddressID"));

                people.add(p);

            } catch(Exception e) { e.printStackTrace(); }
        }
        response = null;
        Person[] arra = new Person[people.size()];
        for(int i = 0; i < arra.length; i++) {
            arra[i] = people.get(i);
        }
        SetPeopleCached(arra);
        return arra;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Address GetAddress(int id) {
        PerformQuery(baseUrl + addressidUrl.replace("{id}", Integer.toString(id)), null); //should block until finished
        Address addr = new Address();
        JSONObject obj = (JSONObject) response;
        try {
            addr.addressStr = obj.getString("AddressStr");
            addr.city = obj.getString("City");
            addr.state = obj.getString("State");
            addr.zip = obj.getString("Zip");
        } catch(Exception e) { e.printStackTrace(); }
        response = null;
        return addr;
    }
    ///////////////////////////////////////////////////////////////////////////////
    Person[] people = null;
    ///////////////////////////////////////////////////////////////////////////////
    public int GetPersonID(Person person) {
        if(GetPeopleCached() == null)
            GetPeople();

        for(Person p : GetPeopleCached()) {
            if(p.firstname.equals(person.firstname) && p.lastname.equals(person.lastname) && p.phonenumber.equals(person.phonenumber))
                return p.idperson;
        }
        return -1;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Person GetPerson(int id) {
        PerformQuery(baseUrl + personUrl.replace("{id}", Integer.toString(id)), null); //should block until finished
        Person people = new Gson().fromJson(response.toString(), Person.class);
        response = null;
        return people;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public void AddPerson(Person person, Address addr, List<Child> children, Availability availability) {
        person.address = addr;
        person.availability = availability;
        person.children = children;
        person.scheduledEvents = null;



        try {
            PerformQuery(baseUrl + peopleUrl, new JSONObject(new Gson().toJson(person))); //should block until finished
        } catch(Exception e) { e.printStackTrace(); }

        response = null;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Address[] GetAddresses() {
        PerformQuery(baseUrl + addressUrl, null);
        Address[] arr = new Gson().fromJson(response.toString(), Address[].class);
        response = null;
        return arr;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private Child[] GetChildren() {
        PerformQuery(baseUrl + childrenUrl, null);
        Child[] children = new Gson().fromJson(response.toString(), Child[].class);
        response = null;
        return children;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public Child[] GetChildrenByParent(int parentid){
        Child[] children = GetChildren();
        List<Child> mChildren = new ArrayList<Child>();
        for(Child c : children) {
            if(c.parentid == parentid)
                mChildren.add(c);
        }
        return (Child[])mChildren.toArray();
    }
    ///////////////////////////////////////////////////////////////////////////////
    private Event[] GetEvents(){
        PerformQuery(baseUrl + eventsUrl, null);
        Event[] event = new Gson().fromJson(response.toString(), Event[].class);
        response = null;
        return event;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public List<Event> GetEventsByParent(int parentid){

        //grab all the events are sort based on teh parentid
        List<Event> mEvents = new ArrayList<Event>();
        Event[] events = GetEvents();
        for(Event e : events) {
            if(e.parentid == parentid)
                mEvents.add(e);
        }
        return mEvents;
    }
    ///////////////////////////////////////////////////////////////////////////////
    public List<Event> GetEventsBySitter(int sitterid){

        //grab all the events are sort based on teh sitterid
        List<Event> mEvents = new ArrayList<Event>();
        Event[] events = GetEvents();
        for(Event e : events) {
            if(e.sitterid == sitterid)
                mEvents.add(e);
        }
        return mEvents;
    }
    ///////////////////////////////////////////////////////////////////////////////
    Object response = null;
    ///////////////////////////////////////////////////////////////////////////////
    Lock lock = new ReentrantLock();
    Lock peopleLock = new ReentrantLock();
    ///////////////////////////////////////////////////////////////////////////////
    private void PerformQuery(String url, JSONObject args) {

        lock.lock();
        try {
            HttpAsyncObject obj = new HttpAsyncObject();
            obj.url = url;
            obj.args = args;
            new HttpAsyncTask().execute(obj);
        } catch (Exception e) {
            Log.e("PerformQuery lock", e.getMessage());
        } finally {
            try {
                while (done == false) //should block
                    Thread.sleep(1000);
            } catch(Exception e) { Log.e("waiting", e.getMessage()); }
        }
        done = false;
        lock.unlock();
    }
    ///////////////////////////////////////////////////////////////////////////////
    boolean done = false;
    ///////////////////////////////////////////////////////////////////////////////
    private class HttpAsyncTask extends AsyncTask<HttpAsyncObject, Void, Object> {

        @Override
        protected Object doInBackground(HttpAsyncObject... httpAsyncObjects) {
            response = JSONParser.getJSONFromUrl(httpAsyncObjects[0].url, httpAsyncObjects[0].args);
            done = true;
            return response;
        }
    }
    ///////////////////////////////////////////////////////////////////////////////
}

//##################################################################################################################////
class APIHelpers {

    ///////////////////////////////////////////////////////////////////////////////
    public static int ParseNewResponseID(Object response) {
        String str = response.toString();
        String sub = str.substring(str.indexOf("id"));
        return Integer.parseInt(sub);
    }
    ///////////////////////////////////////////////////////////////////////////////
    private static String ComposeTimes(int[] times) {
        String ret = "";
        for(int i : times) {
            ret += i + ":";
        }
        return ret;
    }
    ///////////////////////////////////////////////////////////////////////////////
    private static int[] DecomposeTimes(String times) {
        int[] ret = new int[7];

        String[] vals = times.split(":");
        int index = 0;
        for(String str : vals) {
            int val = Integer.parseInt(str);
            ret[index++] = val;
        }
        return ret;
    }
    ///////////////////////////////////////////////////////////////////////////////
}
