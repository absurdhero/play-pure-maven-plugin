package models;

import java.util.ArrayList;
import java.util.List;

import play.*;
import play.mvc.*;
import play.db.ebean.Model;

import javax.persistence.Id;
import javax.persistence.Entity;

@Entity
public class Sample extends Model {
    
        @Id
        public Long id;
	public String property = "property";
	public List<String> stringList = new ArrayList<String>();
	
	public Sample() { }
	
	public String toString() {
		return "property: " + property + "\nlist count: "+stringList.size();
	}
}
