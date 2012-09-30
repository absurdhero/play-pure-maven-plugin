package controllers;

import play.*;
import play.mvc.*;

public class Sample extends Controller {
    static public Result index() {
        return ok(views.html.index.render());
    }
}
