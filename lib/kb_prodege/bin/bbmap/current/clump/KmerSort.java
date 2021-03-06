package clump;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import bloom.KCountArray;
import fileIO.ByteFile;
import fileIO.FileFormat;
import fileIO.ReadWrite;
import jgi.BBMerge;
import shared.Parser;
import shared.PreParser;
import shared.ReadStats;
import shared.Shared;
import shared.Timer;
import shared.Tools;
import sort.ReadComparatorID;
import sort.ReadComparatorName;
import stream.ConcurrentReadInputStream;
import stream.ConcurrentReadOutputStream;
import stream.FASTQ;
import stream.FastaReadInputStream;
import stream.Read;
import structures.ListNum;
import structures.Quantizer;

/**
 * @author Brian Bushnell
 * @date June 20, 2014
 *
 */
public class KmerSort {
	
	/*--------------------------------------------------------------*/
	/*----------------        Initialization        ----------------*/
	/*--------------------------------------------------------------*/
	
	/**
	 * Code entrance from the command line.
	 * @param args Command line arguments
	 */
	public static void main(String[] args){
		final boolean pigz=ReadWrite.USE_PIGZ, unpigz=ReadWrite.USE_UNPIGZ;
		final float ztd=ReadWrite.ZIP_THREAD_MULT;
		final int mzt=ReadWrite.MAX_ZIP_THREADS;
		Timer t=new Timer();
		KmerSort x=new KmerSort(args);
		x.process(t);
		ReadWrite.USE_PIGZ=pigz;
		ReadWrite.USE_UNPIGZ=unpigz;
		ReadWrite.ZIP_THREAD_MULT=ztd;
		ReadWrite.MAX_ZIP_THREADS=mzt;
		
		//Close the print stream if it was redirected
		Shared.closeStream(x.outstream);
	}
	
	/**
	 * Constructor.
	 * @param args Command line arguments
	 */
	public KmerSort(String[] args){
		
		{//Preparse block for help, config files, and outstream
			PreParser pp=new PreParser(args, getClass(), false);
			args=pp.args;
			outstream=pp.outstream;
		}
		
		ReadWrite.USE_PIGZ=ReadWrite.USE_UNPIGZ=true;
		ReadWrite.MAX_ZIP_THREADS=Shared.threads();
		
		Parser parser=new Parser();
		for(int i=0; i<args.length; i++){
			String arg=args[i];
			String[] split=arg.split("=");
			String a=split[0].toLowerCase();
			String b=split.length>1 ? split[1] : null;

			if(a.equals("verbose")){
				verbose=KmerComparator.verbose=Tools.parseBoolean(b);
			}else if(a.equals("parse_flag_goes_here")){
				//Set a variable here
			}else if(a.equals("k")){
				k=Integer.parseInt(b);
				assert(k>0 && k<32);
			}else if(a.equals("mincount") || a.equals("mincr")){
				minCount=Integer.parseInt(b);
			}else if(a.equals("rename") || a.equals("addname")){
				addName=Tools.parseBoolean(b);
			}else if(a.equals("shortname") || a.equals("shortnames")){
				if(b!=null && b.equals("shrink")){
					shrinkName=true;
				}else{
					shrinkName=false;
					shortName=Tools.parseBoolean(b);
				}
			}else if(a.equals("rcomp") || a.equals("reversecomplement")){
				rcomp=Tools.parseBoolean(b);
			}else if(a.equals("ecco")){
				ecco=Tools.parseBoolean(b);
			}else if(a.equals("condense") || a.equals("consensus") || a.equals("concensus")){//Note the last one is intentionally misspelled
				condense=Tools.parseBoolean(b);
			}else if(a.equals("correct") || a.equals("ecc")){
				correct=Tools.parseBoolean(b);
			}else if(a.equals("passes")){
				passes=Integer.parseInt(b);
			}
			
			else if(a.equals("dedupe")){
				dedupe=Tools.parseBoolean(b);
			}else if(a.equals("markduplicates")){
				dedupe=Clump.markOnly=Tools.parseBoolean(b);
			}else if(a.equals("markall")){
				boolean x=Tools.parseBoolean(b);
				if(x){
					dedupe=Clump.markOnly=Clump.markAll=true;
				}else{
					Clump.markAll=false;
				}
			}
			
			else if(a.equals("prefilter")){
				KmerReduce.prefilter=Tools.parseBoolean(b);
			}else if(a.equals("groups") || a.equals("g") || a.equals("sets") || a.equals("ways")){
				groups=Integer.parseInt(b);
				splitInput=(groups>1);
			}else if(a.equals("seed")){
				KmerComparator.defaultSeed=Long.parseLong(b);
			}else if(a.equals("hashes")){
				KmerComparator.setHashes(Integer.parseInt(b));
			}else if(a.equals("border")){
				KmerComparator.defaultBorder=Integer.parseInt(b);
			}else if(a.equals("minprob")){
				KmerComparator.minProb=Float.parseFloat(b);
				
			}else if(a.equals("unpair")){
				unpair=Tools.parseBoolean(b);
			}else if(a.equals("repair")){
				repair=Tools.parseBoolean(b);
			}else if(a.equals("namesort") || a.equals("sort")){
				namesort=Tools.parseBoolean(b);
			}else if(a.equals("reorder") || a.equals("reorderclumps") || a.equals("reordermode")){
				reorderMode=REORDER_AUTO;
				if(b==null || b.equalsIgnoreCase("auto") || b.equalsIgnoreCase("a")){
					reorderMode=REORDER_AUTO;
				}else if(b.equalsIgnoreCase("unpaired") || b.equalsIgnoreCase("consensus") || b.equalsIgnoreCase("reorder") || b.equalsIgnoreCase("c")){
					reorderMode=REORDER_CONSENSUS;
				}else if(b.equalsIgnoreCase("pair") || b.equalsIgnoreCase("pairs") || b.equalsIgnoreCase("paired") || b.equalsIgnoreCase("p")){
					reorderMode=REORDER_PAIRED;
				}else{
					boolean x=Tools.parseBoolean(b);
					if(x){
						reorderMode=REORDER_AUTO;
					}else{
						reorderMode=REORDER_FALSE;
					}
				}
			}else if(a.equals("reorderpaired") || a.equals("reorderclumpspaired")){
				boolean x=Tools.parseBoolean(b);
				if(x){
					reorderMode=REORDER_PAIRED;
				}else{
					reorderMode=REORDER_FALSE;
				}
			}
			
			else if(a.equals("fetchthreads")){
				//Do nothing
			}else if(Clump.parseStatic(arg, a, b)){
				//Do nothing
			}else if(parser.parse(arg, a, b)){
				//do nothing
			}
			
			else{
				outstream.println("Unknown parameter "+args[i]);
				assert(false) : "Unknown parameter "+args[i];
				//				throw new RuntimeException("Unknown parameter "+args[i]);
			}
		}
		Clump.renameConsensus=condense;
		if(dedupe){KmerComparator.compareSequence=true;}
		assert(!(reorderMode==REORDER_PAIRED && dedupe)) : "REORDER_PAIRED and dedupe are incompatible.";
		
		{//Process parser fields
			Parser.processQuality();
			
			maxReads=parser.maxReads;
			
			overwrite=ReadStats.overwrite=parser.overwrite;
			append=ReadStats.append=parser.append;

			in1=parser.in1;
			in2=parser.in2;

			out1=parser.out1;
			out2=parser.out2;
			
			extin=parser.extin;
			extout=parser.extout;
		}
		
		assert(FastaReadInputStream.settingsOK());
		
		if(in1!=null && in2==null && in1.indexOf('#')>-1 && !new File(in1).exists()){
			in2=in1.replace("#", "2");
			in1=in1.replace("#", "1");
		}
		if(in2!=null){
			if(FASTQ.FORCE_INTERLEAVED){outstream.println("Reset INTERLEAVED to false because paired input files were specified.");}
			FASTQ.FORCE_INTERLEAVED=FASTQ.TEST_INTERLEAVED=false;
		}
		
		if(in1==null){throw new RuntimeException("Error - at least one input file is required.");}
		if(!ByteFile.FORCE_MODE_BF1 && !ByteFile.FORCE_MODE_BF2 && Shared.threads()>2){
			ByteFile.FORCE_MODE_BF2=true;
		}

		if(out1!=null && out1.equalsIgnoreCase("null")){out1=null;}
		if(out1!=null && out2==null && out1.indexOf('#')>-1){
			out2=out1.replace("#", "2");
			out1=out1.replace("#", "1");
		}
		
		if(!Tools.testOutputFiles(overwrite, append, false, out1)){
			outstream.println((out1==null)+", "+out1);
			throw new RuntimeException("\n\noverwrite="+overwrite+"; Can't write to output files "+out1+"\n");
		}

		ffout1=FileFormat.testOutput(out1, FileFormat.FASTQ, extout, true, overwrite, append, false);
		ffout2=FileFormat.testOutput(out2, FileFormat.FASTQ, extout, true, overwrite, append, false);
		
		if(groups>1 && in1.contains("%") && (splitInput || !new File(in1).exists())){
			ffin1=new FileFormat[groups];
			ffin2=new FileFormat[groups];
			for(int i=0; i<groups; i++){
				ffin1[i]=FileFormat.testInput(in1.replaceFirst("%", ""+i), FileFormat.FASTQ, extin, true, true);
				ffin2[i]=in2==null ? null : FileFormat.testInput(in2.replaceFirst("%", ""+i), FileFormat.FASTQ, extin, true, true);
			}
		}else{
			assert(!in1.contains("%") && groups==1) : "The % symbol must only be present in the input filename if groups>1.";
			ffin1=new FileFormat[1];
			ffin1[0]=FileFormat.testInput(in1, FileFormat.FASTQ, extin, true, true);
			ffin2=new FileFormat[1];
			ffin2[0]=FileFormat.testInput(in2, FileFormat.FASTQ, extin, true, true);
			groups=1;
		}
//		if(groups>1){ReadWrite.USE_UNPIGZ=false;} //Not needed since they are not concurrent
		
		if((reorderMode!=REORDER_FALSE) && (passes>1 || condense || correct || groups>1)){
			outstream.println("Clump reordering disabled because "+(passes>1 ? "passes>1" : condense ? " condense=t" : correct ? " ecc=t" : "groups>1"));
			reorderMode=REORDER_FALSE;
		}
		
		if(reorderMode==REORDER_PAIRED){
			if(!unpair || !repair){
				outstream.println("Unpair and repair enabled because clump reorder mode is set to paired.");
				unpair=true;
				repair=true;
			}
		}
	}
	
	
	/*--------------------------------------------------------------*/
	/*----------------         Outer Methods        ----------------*/
	/*--------------------------------------------------------------*/

	/** Count kmers */
	void preprocess(){
		if(minCount>1){
			if(groups>1){
				table=ClumpTools.table();
				assert(table!=null);
			}else{
				Timer ctimer=new Timer();
				if(verbose){ctimer.start("Counting pivots.");}
				table=ClumpTools.getTable(in1, in2, k, minCount);
				if(verbose){ctimer.stop("Count time: ");}
			}
		}
	}

	/** Create read streams and process all data */
	void process(Timer t){
		
		preprocess();
		
//		final ConcurrentReadInputStream[] cris=new ConcurrentReadInputStream[groups];
//		for(int i=0; i<cris.length; i++){
//			cris[i]=ConcurrentReadInputStream.getReadInputStream(maxReads, true, ffin1[i], ffin2[i], null, null);
//		}

		final ConcurrentReadOutputStream ros;
		if(out1!=null){
			final int buff=1; //This prevents more than 2 sets of reads from being in memory at once.
			assert(!out1.equalsIgnoreCase(in1)) : "Input file and output file have same name.";
			
			ros=ConcurrentReadOutputStream.getStream(ffout1, ffout2, null, null, buff, null, useSharedHeader);
			ros.start();
		}else{ros=null;}
		
		readsProcessed=basesProcessed=diskProcessed=memProcessed=0;
		
		//Process the read stream
		processInner(ros);
		
		table=null;
		ClumpTools.clearTable();
		
		errorState|=ReadStats.writeAll();
		
		t.stop();
		
		double rpnano=readsProcessed/(double)(t.elapsed);
		double bpnano=basesProcessed/(double)(t.elapsed);

		String rpstring=(readsProcessed<100000 ? ""+readsProcessed : readsProcessed<100000000 ? (readsProcessed/1000)+"k" : (readsProcessed/1000000)+"m");
		String rpstring2=readsProcessed+"";
		String bpstring=(basesProcessed<100000 ? ""+basesProcessed : basesProcessed<100000000 ? (basesProcessed/1000)+"k" : (basesProcessed/1000000)+"m");
		
		String cpstring=""+(groups==1 ? clumpsProcessedThisPass : clumpsProcessedTotal);
		String epstring=""+correctionsTotal;
		String dpstring=""+duplicatesTotal;

		String rostring=""+readsOut;
		String bostring=""+basesOut;
		
		while(rpstring.length()<10){rpstring=" "+rpstring;}
		while(bpstring.length()<10){bpstring=" "+bpstring;}

		while(rpstring2.length()<12){rpstring2=" "+rpstring2;}
		while(cpstring.length()<12){cpstring=" "+cpstring;}
		while(epstring.length()<12){epstring=" "+epstring;}
		while(dpstring.length()<12){dpstring=" "+dpstring;}

		while(rostring.length()<12){rostring=" "+rostring;}
		while(bostring.length()<12){bostring=" "+bostring;}
		
		outstream.println("Time:                             \t"+t);
		outstream.println("Reads Processed:    "+rpstring+"  \t"+String.format(Locale.ROOT, "%.2fk reads/sec", rpnano*1000000));
		outstream.println("Bases Processed:    "+bpstring+"  \t"+String.format(Locale.ROOT, "%.2fm bases/sec", bpnano*1000));
		outstream.println();

		outstream.println("Reads In:         "+rpstring2);
		outstream.println("Clumps Formed:    "+cpstring);
		if(correct){
			outstream.println("Errors Corrected: "+epstring);
		}
		if(dedupe){
			outstream.println("Duplicates Found: "+dpstring);
			outstream.println("Reads Out:        "+rostring);
			outstream.println("Bases Out:        "+bostring);
		}
		
		if(errorState){
			Clumpify.sharedErrorState=true;
			throw new RuntimeException(getClass().getName()+" terminated in an error state; the output may be corrupt.");
		}
	}
	
	/** Collect and sort the reads */
	void processInner(final ConcurrentReadOutputStream ros){
		if(verbose){outstream.println("Making comparator.");}
		KmerComparator kc=new KmerComparator(k, addName, (rcomp || condense || correct));
		
		ClumpList.UNRCOMP=(!rcomp && !condense);
		Timer t=new Timer();
		
		final int conservativePasses=Clump.conservativeFlag ? passes : Tools.max(1, passes/2);
		if(groups==1 && passes>1){Clump.setConservative(true);}

		useSharedHeader=(ffin1[0].samOrBam() && ffout1!=null && ffout1.samOrBam());
		
		for(int group=0; group<groups; group++){
			if(verbose){outstream.println("Starting cris "+group+".");}
			
			final ConcurrentReadInputStream cris=ConcurrentReadInputStream.getReadInputStream(maxReads,
					useSharedHeader && groups==1, ffin1[group], ffin2[group], null, null);
			cris.start();
			if(reorderMode!=REORDER_FALSE){
				assert(groups==1) : "Too many groups for reorder: "+groups;
				if(reorderMode==REORDER_AUTO){
					if(cris.paired() && !dedupe){
						reorderMode=REORDER_PAIRED;
						unpair=repair=true;
					}else{
						reorderMode=REORDER_CONSENSUS;
					}
				}
			}
			
			if(verbose){t.start("Fetching reads.");}
			ArrayList<Read> reads=fetchReads(cris, kc);
			quantizeQuality=false;
//			if(verbose){t.stop("Fetch time: ");}
			
			if(verbose){t.start("Sorting.");}
			Shared.sort(reads, kc);
			if(verbose){t.stop("Sort time: ");}
			
//			if(verbose){t.start("Counting clumps.");}
//			clumpsProcessed+=countClumps(reads);
//			if(verbose){t.stop("Count time: ");}
			
			if(verbose){t.start("Making clumps.");}
			readsProcessedThisPass=reads.size();
			
			ClumpList cl=new ClumpList(reads, k, reorderMode==REORDER_CONSENSUS);
			
			if(reorderMode!=REORDER_FALSE){
				reads.clear();
				if(reorderMode==REORDER_PAIRED){cl.reorderPaired();}
				else if(reorderMode==REORDER_CONSENSUS){cl.reorder();}
				else{assert(false) : reorderMode;}
				for(Clump c : cl){
					reads.addAll(c);
				}
			}
			
			clumpsProcessedThisPass=cl.size();
			clumpsProcessedTotal+=clumpsProcessedThisPass;
			if(verbose){t.stop("Clump time: ");}
			
			if(dedupe){
				reads.clear();
				if(verbose){t.start("Deduping.");}
				reads=processClumps(cl, ClumpList.DEDUPE);
				
				if(passes>1 && groups==1){
					
					FASTQ.DETECT_QUALITY=FASTQ.DETECT_QUALITY_OUT=false;
					FASTQ.ASCII_OFFSET=FASTQ.ASCII_OFFSET_OUT;
					
					if(verbose){outstream.println("Pass 1.\n");}
					if(verbose){outstream.println("Reads:        \t"+readsProcessedThisPass);}
					outstream.println("Clumps:       \t"+clumpsProcessedThisPass);
					
					for(int pass=1; pass<passes; pass++){
						
						kc=new KmerComparator(k, kc.seed<0 ? -1 : kc.seed+1, kc.border-1, kc.hashes, false, kc.rcompReads);
						reads=runOnePass(reads, kc);

						if(verbose){outstream.println("Seed: "+kc.seed);}
						if(verbose){outstream.println("Pass "+(pass+1)+".");}
						outstream.println();
					}
				}
				
				if(verbose){t.stop("Dedupe time: ");}
			}else if(condense){
				reads.clear();
				if(verbose){t.start("Condensing.");}
				reads=processClumps(cl, ClumpList.CONDENSE);
				if(verbose){t.stop("Condense time: ");}
			}else if(correct){
				reads.clear();
				if(verbose){t.start("Correcting.");}
				reads=processClumps(cl, ClumpList.CORRECT);
				if(verbose){t.stop("Correct time: ");}
				
				if(verbose){outstream.println("Seed: "+kc.seed);}
				if(groups>1){
					if(verbose){outstream.println("Reads:        \t"+readsProcessedThisPass);}
					outstream.println("Clumps:       \t"+clumpsProcessedThisPass);
					if(correct){
						outstream.println("Corrections:  \t"+correctionsThisPass);
					}
					outstream.println();
				}
				
				if(passes>1 && groups==1){
					
					FASTQ.DETECT_QUALITY=FASTQ.DETECT_QUALITY_OUT=false;
					FASTQ.ASCII_OFFSET=FASTQ.ASCII_OFFSET_OUT;
					
					if(verbose){outstream.println("Pass 1.");}
					if(verbose){outstream.println("Reads:        \t"+readsProcessedThisPass);}
					outstream.println("Clumps:       \t"+clumpsProcessedThisPass);
					if(correct){
						outstream.println("Corrections:  \t"+correctionsThisPass);
					}
					outstream.println();
					
					for(int pass=1; pass<passes; pass++){
						
						if(pass>=conservativePasses){Clump.setConservative(false);}
						
						kc=new KmerComparator(k, kc.seed<0 ? -1 : kc.seed+1, kc.border-1, kc.hashes, false, kc.rcompReads);
						reads=runOnePass(reads, kc);

						if(verbose){outstream.println("Seed: "+kc.seed);}
						if(verbose){outstream.println("Pass "+(pass+1)+".");}
						if(verbose){outstream.println("Reads:        \t"+readsProcessedThisPass);}
						outstream.println("Clumps:       \t"+clumpsProcessedThisPass);
						if(correct){
							outstream.println("Corrections:  \t"+correctionsThisPass);
						}
						outstream.println();
					}
				}
			}
			
			if(repair || namesort){
				if(groups>1){
					if(verbose){t.start("Name-sorting.");}
					reads=nameSort(reads, false);
					if(verbose){t.stop("Sort time: ");}
				}else{
					if(namesort){
						if(verbose){t.start("Name-sorting.");}
						reads=idSort(reads, repair);
						if(verbose){t.stop("Sort time: ");}
					}else{
						reads=read1Only(reads);
					}
				}
			}
			
			if(ros!=null){
				assert(reads.size()==readsProcessedThisPass || dedupe || condense ||
						(reads.size()*2==readsProcessedThisPass && repair)) :
					reads.size()+", "+readsProcessedThisPass;
				if(verbose){t.start("Writing.");}
				for(Read r : reads){
					readsOut+=r.pairCount();
					basesOut+=r.pairLength();
				}
				ros.add(reads, 0);
			}
		}
		
		if(ros!=null){
			if(verbose){outstream.println("Waiting for writing to complete.");}
			errorState=ReadWrite.closeStream(ros)|errorState;
			if(verbose){t.stop("Write time: ");}
		}
		
		if(verbose){outstream.println("Done!");}
	}
	
	private ArrayList<Read> runOnePass(ArrayList<Read> reads, KmerComparator kc){
//		for(Read r : reads){r.obj=null;}//No longer necessary
		
		Timer t=new Timer();
		
		table=null;
		if(minCount>1){
			if(verbose){t.start("Counting pivots.");}
			table=ClumpTools.getTable(reads, k, minCount);
			if(verbose){t.stop("Count time: ");}
		}
		
		if(verbose){t.start("Hashing.");}
		kc.hashThreaded(reads, table, minCount);
		if(verbose){t.stop("Hash time: ");}
		
		if(verbose){t.start("Sorting.");}
		Shared.sort(reads, kc);
//		Shared.sort(reads, kc);
		if(verbose){t.stop("Sort time: ");}
		
		if(verbose){t.start("Making clumps.");}
		readsProcessedThisPass=reads.size();
		ClumpList cl=new ClumpList(reads, k, false);
		reads.clear();
		clumpsProcessedThisPass=cl.size();
		clumpsProcessedTotal+=clumpsProcessedThisPass;
		if(verbose){t.stop("Clump time: ");}
		
		if(correct){
			if(verbose){t.start("Correcting.");}
			reads=processClumps(cl, ClumpList.CORRECT);
			if(verbose){t.stop("Correct time: ");}
		}else{
			assert(dedupe);
			if(verbose){t.start("Deduplicating.");}
			reads=processClumps(cl, ClumpList.DEDUPE);
			if(verbose){t.stop("Dedupe time: ");}
		}
		
		return reads;
	}
	
	private static ArrayList<Read> nameSort(ArrayList<Read> list, boolean pair){
//		Shared.sort(list, ReadComparatorName.comparator);
		Shared.sort(list, ReadComparatorName.comparator);
		if(!pair){return list;}
		
		ArrayList<Read> list2=new ArrayList<Read>(1+list.size()/2);
		
		Read prev=null;
		for(Read r : list){
			if(prev==null){
				prev=r;
				assert(prev.mate==null);
			}else{
				if(prev.id.equals(r.id) || FASTQ.testPairNames(prev.id, r.id, true)){
					prev.mate=r;
					r.mate=prev;
					prev.setPairnum(0);
					r.setPairnum(1);
					list2.add(prev);
					prev=null;
				}else{
					list2.add(prev);
					prev=r;
				}
			}
		}
		return list2;
	}
	
	private static ArrayList<Read> idSort(ArrayList<Read> list, boolean pair){
		Shared.sort(list, ReadComparatorID.comparator);
		if(!pair){return list;}
		
		ArrayList<Read> list2=new ArrayList<Read>(1+list.size()/2);
		
		Read prev=null;
		for(Read r : list){
			if(prev==null){
				prev=r;
				assert(prev.mate==null);
			}else{
				if(prev.numericID==r.numericID){
					assert(prev.pairnum()==0 && r.pairnum()==1) : prev.id+"\n"+r.id;
					prev.mate=r;
					r.mate=prev;
					prev.setPairnum(0);
					r.setPairnum(1);
					list2.add(prev);
					prev=null;
				}else{
					list2.add(prev);
					prev=r;
				}
			}
		}
		return list2;
	}
	
	private static ArrayList<Read> read1Only(ArrayList<Read> list){
		ArrayList<Read> list2=new ArrayList<Read>(1+list.size()/2);
		for(Read r : list){
			assert(r.mate!=null) : r+"\n"+r.mate;
			if(r.pairnum()==0){list2.add(r);}
		}
		return list2;
	}
	
//	@Deprecated
//	//No longer needed
//	public int countClumps(ArrayList<Read> list){
//		int count=0;
//		long currentKmer=-1;
//		for(final Read r : list){
//			final ReadKey key=(ReadKey)r.obj;
//			if(key.kmer!=currentKmer){
//				currentKmer=key.kmer;
//				count++;
//			}
//		}
//		return count;
//	}
	
	public ArrayList<Read> fetchReads(final ConcurrentReadInputStream cris, final KmerComparator kc){
		Timer t=new Timer();
		if(verbose){t.start("Making fetch threads.");}
		final int threads=Shared.threads();
		ArrayList<FetchThread> alht=new ArrayList<FetchThread>(threads);
		for(int i=0; i<threads; i++){alht.add(new FetchThread(i, cris, kc, unpair));}
		
		readsThisPass=memThisPass=0;
		
		if(verbose){outstream.println("Starting threads.");}
		for(FetchThread ht : alht){ht.start();}
		
		
		if(verbose){outstream.println("Waiting for threads.");}
		/* Wait for threads to die */
		for(FetchThread ht : alht){
			
			/* Wait for a thread to die */
			while(ht.getState()!=Thread.State.TERMINATED){
				try {
					ht.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			readsThisPass+=ht.readsProcessedT;
			basesProcessed+=ht.basesProcessedT;
			diskProcessed+=ht.diskProcessedT;
			memThisPass+=ht.memProcessedT;
		}
		readsProcessed+=readsThisPass;
		memProcessed+=memThisPass;

		if(verbose){t.stop("Fetch time: ");}
		if(verbose){System.err.println("Closing input stream.");}
		errorState=ReadWrite.closeStream(cris)|errorState;
		
		if(verbose){t.start("Combining thread output.");}
		assert(readsProcessed<=Integer.MAX_VALUE) :
			"\nThe number of reads is greater than 2 billion, which is the limit for a single group. "
			+ "\nPlease rerun and manually specify 'groups=7' or similar, "
			+ "\nsuch that the number of reads per group is less than 2 billion.";
		ArrayList<Read> list=new ArrayList<Read>((int)readsThisPass);
		for(int i=0; i<threads; i++){
			FetchThread ht=alht.set(i, null);
			list.addAll(ht.storage);
		}
		if(verbose){t.stop("Combine time: ");}
		
		assert(list.size()==readsThisPass || (cris.paired() && list.size()*2==readsThisPass)) : list.size()+", "+readsThisPass+", "+cris.paired();
		ecco=false;
		return list;
	}
	
	public ArrayList<Read> processClumps(ClumpList cl, int mode){
		long[] rvector=new long[2];
		ArrayList<Read> out=cl.process(Shared.threads(), mode, rvector);
		correctionsThisPass=rvector[0];
		correctionsTotal+=correctionsThisPass;
		duplicatesThisPass=rvector[1];
		duplicatesTotal+=duplicatesThisPass;
//		assert(false) : duplicatesTotal+", "+duplicatesThisPass;
		cl.clear();
		return out;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Methods        ----------------*/
	/*--------------------------------------------------------------*/
	
	/*--------------------------------------------------------------*/
	/*----------------         Inner Classes        ----------------*/
	/*--------------------------------------------------------------*/
	
	private class FetchThread extends Thread{
		
		FetchThread(int id_, ConcurrentReadInputStream cris_, KmerComparator kc_, boolean unpair_){
			id=id_;
			cris=cris_;
			kc=kc_;
			storage=new ArrayList<Read>();
			unpairT=unpair_;
		}
		
		@Override
		public void run(){
			ListNum<Read> ln=cris.nextList();
			final boolean paired=cris.paired();
			ArrayList<Read> reads=(ln!=null ? ln.list : null);
			
			while(reads!=null && reads.size()>0){
				
				for(Read r : reads){
					if(!r.validated()){
						r.validate(true);
						if(r.mate!=null){r.mate.validate(true);}
					}
					readsProcessedT+=1+r.mateCount();
					basesProcessedT+=r.length()+r.mateLength();
//					diskProcessedT+=r.countFastqBytes()+r.countMateFastqBytes();
//					memProcessedT+=r.countBytes()+r.countMateBytes();
					if(shrinkName){
						Clumpify.shrinkName(r);
						Clumpify.shrinkName(r.mate);
					}else if(shortName){
						Clumpify.shortName(r);
						Clumpify.shortName(r.mate);
					}
					
					if(quantizeQuality){
						Quantizer.quantize(r, r.mate);
					}
				}
				
				if(ecco){
					for(Read r : reads){
						Read r2=r.mate;
						assert(r.obj==null) : "TODO: Pivot should not have been generated yet, though it may be OK.";
						assert(r2!=null) : "ecco requires paired reads.";
						if(r2!=null){
							int x=BBMerge.findOverlapStrict(r, r2, true);
							if(x>=0){
								r.obj=null;
								r2.obj=null;
							}
						}
					}
				}
				
				ArrayList<Read> hashList=reads;
				if(paired && unpairT){
					hashList=new ArrayList<Read>(reads.size()*2);
					for(Read r1 : reads){
						Read r2=r1.mate;
						assert(r2!=null);
						hashList.add(r1);
						hashList.add(r2);
						if(groups>1 || !repair || namesort){
							r1.mate=null;
							r2.mate=null;
						}
					}
				}
				
				kc.hash(hashList, table, minCount, true);
				storage.addAll(hashList);
				cris.returnList(ln.id, ln.list.isEmpty());
				ln=cris.nextList();
				reads=(ln!=null ? ln.list : null);
			}
			if(ln!=null){
				cris.returnList(ln.id, ln.list==null || ln.list.isEmpty());
			}
			
			//Optimization for TimSort
			if(parallelSort){
				storage.sort(kc);
//				Shared.sort(storage, kc); //Already threaded; this is not needed.
			}else{
				Collections.sort(storage, kc);
			}
		}

		final int id;
		final ConcurrentReadInputStream cris;
		final KmerComparator kc;
		final ArrayList<Read> storage;
		final boolean unpairT;
		
		protected long readsProcessedT=0;
		protected long basesProcessedT=0;
		protected long diskProcessedT=0;
		protected long memProcessedT=0;
	}
	
	/*--------------------------------------------------------------*/
	/*----------------            Fields            ----------------*/
	/*--------------------------------------------------------------*/

	private int k=31;
	int minCount=0;
	
	int groups=1;
	
	KCountArray table=null;
	
	/*--------------------------------------------------------------*/
	/*----------------          I/O Fields          ----------------*/
	/*--------------------------------------------------------------*/

	private String in1=null;
	private String in2=null;

	private String out1=null;
	private String out2=null;
	
	private String extin=null;
	private String extout=null;
	
	/*--------------------------------------------------------------*/
	
	protected long readsProcessed=0;
	protected long basesProcessed=0;
	protected long diskProcessed=0;
	protected long memProcessed=0;

	protected long readsOut=0;
	protected long basesOut=0;

	protected long readsThisPass=0;
	protected long memThisPass=0;

	protected long readsProcessedThisPass=0;
	protected long clumpsProcessedThisPass=0;
	protected long correctionsThisPass=0;
	
	protected long duplicatesThisPass=0;
	protected static long duplicatesTotal=0;
	
	protected long clumpsProcessedTotal=0;
	protected static long correctionsTotal=0;
	
	protected int passes=1;
	
	private long maxReads=-1;
	private boolean addName=false;
	boolean shortName=false;
	boolean shrinkName=false;
	private boolean rcomp=false;
	private boolean condense=false;
	private boolean correct=false;
	private boolean dedupe=false;
	private boolean splitInput=false;
	boolean ecco=false;
	private boolean unpair=false;
	boolean repair=false;
	boolean namesort=false;
	private boolean useSharedHeader=false;
	
	private int reorderMode=REORDER_FALSE;
	
	
	
//	private boolean reorder=false;
//	private boolean reorderpaired=false;
	
	final boolean parallelSort=Shared.parallelSort;
	

	static boolean quantizeQuality=false;
	
	/*--------------------------------------------------------------*/
	
	static final int REORDER_FALSE=0, REORDER_CONSENSUS=1, REORDER_PAIRED=2, REORDER_AUTO=3;
	
	/*--------------------------------------------------------------*/
	/*----------------         Final Fields         ----------------*/
	/*--------------------------------------------------------------*/

	private final FileFormat ffin1[];
	private final FileFormat ffin2[];

	private final FileFormat ffout1;
	private final FileFormat ffout2;
	
	/*--------------------------------------------------------------*/
	/*----------------        Common Fields         ----------------*/
	/*--------------------------------------------------------------*/
	
	private PrintStream outstream=System.err;
	public static boolean verbose=true;
	public boolean errorState=false;
	private boolean overwrite=false;
	private boolean append=false;
	
}
