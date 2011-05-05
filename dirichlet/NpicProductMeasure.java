package edu.cmu.ml.rtw.dirichlet;


public class NpicProductMeasure implements Distribution<Npic> {

    private Distribution<String> npDist;
    private Distribution<String> contextDist;

    public NpicProductMeasure(Distribution<String> npDist, Distribution<String> contextDist) {
	this.npDist = npDist;
	this.contextDist = contextDist;
    }

    public Npic sample() {
	String np = npDist.sample();
	String context = contextDist.sample();
	return new Npic(np, context);
    }

    public double getDensity(Npic point) {
	return npDist.getDensity(point.getNp()) * contextDist.getDensity(point.getContext());
    }

    public double getDensity(LabeledVector<Npic> iidPonts) {
	throw new RuntimeException("");
    }

    public String toString() {
	StringBuilder sb = new StringBuilder();
	sb.append("NPs:\n");
	sb.append(npDist.toString());
	sb.append("\ncontexts:\n");
	sb.append(contextDist.toString());
	return sb.toString();
    }
}