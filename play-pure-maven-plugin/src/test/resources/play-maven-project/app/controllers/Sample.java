package controllers;

import play.*;
import play.mvc.*;

public class Sample extends Controller {
    static public Result index() {
    	System.out.println(new models.Sample().toString());
        return ok(views.html.index.render());
    }
}
