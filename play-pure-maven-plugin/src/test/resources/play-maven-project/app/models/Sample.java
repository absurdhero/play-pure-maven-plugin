package models;

import java.util.ArrayList;
import java.util.List;

import play.*;
import play.mvc.*;
import play.db.ebean.Model;

public class Sample extends Model {
    
	public String property = "property";
	public List<String> stringList = new ArrayList<String>();
	
	public Sample() { }
	
	public String toString() {
		return "property: " + property + "\nlist count: "+stringList.size();
	}
}
