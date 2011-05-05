package edu.cmu.ml.rtw.dirichlet;

public class Npic {

    private String np;
    private String context;

    public Npic(String np, String context) {
	this.np = np;
	this.context = context;
    }

    public String getNp() {
	return np;
    }

    public String getContext() {
	return context;
    }

    public int hashCode() {
	return np.hashCode() * 7 + context.hashCode();
    }

    public boolean equals(Object o) {
	if (o instanceof Npic) {
	    Npic n = (Npic) o;
	    return this.np.equals(n.np) && this.context.equals(n.context);
	}
	return false;
    }

    public String toString() {
	return "(" + np + "," + context + ")";
    }
}