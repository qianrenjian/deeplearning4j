package org.deeplearning4j.datasets.canova;

import lombok.AllArgsConstructor;
import org.canova.api.records.reader.RecordReader;
import org.canova.api.records.reader.SequenceRecordReader;
import org.canova.api.writable.Writable;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.MultiDataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

import java.util.*;

/**RecordReaderMultiDataSetIterator: An iterator for data from multiple RecordReaders and SequenceRecordReaders
 * The idea: generate multiple inputs and multiple outputs from one or more Sequence/RecordReaders. Inputs and outputs
 * may be obtained from subsets of the RecordReader and SequenceRecordReaders columns (for examples, some inputs and outputs
 * as different columns in the same file); it is also possible to mix different types of data (for example, using both
 * RecordReaders and SequenceRecordReaders in the same RecordReaderMultiDataSetIterator).
 * @author Alex Black
 */
public class RecordReaderMultiDataSetIterator implements MultiDataSetIterator {

    public enum AlignmentMode {
        EQUAL_LENGTH,
        ALIGN_START,
        ALIGN_END
    }

    private int batchSize;
    private AlignmentMode alignmentMode;
    private Map<String,RecordReader> recordReaders = new HashMap<>();
    private Map<String,SequenceRecordReader> sequenceRecordReaders = new HashMap<>();

    private List<SubsetDetails> inputs = new ArrayList<>();
    private List<SubsetDetails> outputs = new ArrayList<>();

    private MultiDataSetPreProcessor preProcessor;

    private RecordReaderMultiDataSetIterator(Builder builder){
        this.batchSize = builder.batchSize;
        this.alignmentMode = builder.alignmentMode;
        this.recordReaders = builder.recordReaders;
        this.sequenceRecordReaders = builder.sequenceRecordReaders;
        this.inputs.addAll(builder.inputs);
        this.outputs.addAll(builder.outputs);
    }

    @Override
    public MultiDataSet next() {
        return next(batchSize);
    }

    @Override
    public MultiDataSet next(int num) {
        if(!hasNext()) throw new NoSuchElementException("No next elements");

        //First: load the next values from the RR / SeqRRs
        Map<String,List<Collection<Writable>>> nextRRVals = new HashMap<>();
        Map<String,List<Collection<Collection<Writable>>>> nextSeqRRVals = new HashMap<>();

        int minExamples = 0;
        for(Map.Entry<String,RecordReader> entry : recordReaders.entrySet() ){
            RecordReader rr = entry.getValue();
            List<Collection<Writable>> writables = new ArrayList<>(num);
            for( int i=0; i<num && rr.hasNext(); i++ ){
                writables.add(rr.next());
            }
            minExamples = Math.min(minExamples,writables.size());

            nextRRVals.put(entry.getKey(), writables);
        }

        for(Map.Entry<String,SequenceRecordReader> entry : sequenceRecordReaders.entrySet() ){
            SequenceRecordReader rr = entry.getValue();
            List<Collection<Collection<Writable>>> writables = new ArrayList<>(num);
            for( int i=0; i<num && rr.hasNext(); i++ ){
                Collection<Collection<Writable>> next = rr.sequenceRecord();
                writables.add(next);
            }
            minExamples = Math.min(minExamples,writables.size());

            nextSeqRRVals.put(entry.getKey(), writables);
        }

        //In order to align data at the end (for each example individually), we need to know the length of the
        // longest time series for each example
        int[] longestSequence = null;
        if(alignmentMode == AlignmentMode.ALIGN_END){
            longestSequence = new int[minExamples];
            for(Map.Entry<String,List<Collection<Collection<Writable>>>> entry : nextSeqRRVals.entrySet()){
                List<Collection<Collection<Writable>>> list = entry.getValue();
                for( int i=0; i<list.size() && i<minExamples; i++ ){
                    longestSequence[i] = Math.max(longestSequence[i],list.get(i).size());
                }
            }
        }

        //Second: create the input arrays
            //To do this, we need to know longest time series length, so we can do padding
        int longestTS = -1;
        if(alignmentMode != AlignmentMode.EQUAL_LENGTH) {
            for (Map.Entry<String, List<Collection<Collection<Writable>>>> entry : nextSeqRRVals.entrySet()) {
                List<Collection<Collection<Writable>>> list = entry.getValue();
                for(Collection<Collection<Writable>> c : list){
                    longestTS = Math.max(longestTS,c.size());
                }
            }
        }

        INDArray[] inputArrs = new INDArray[inputs.size()];
        INDArray[] inputArrMasks = new INDArray[inputs.size()];
        boolean inputMasks = false;
        int i=0;
        for(SubsetDetails d : inputs){
            if(nextRRVals.containsKey(d.readerName)) {
                //Standard reader
                List<Collection<Writable>> list = nextRRVals.get(d.readerName);
                inputArrs[i] = convertWritables(list,minExamples,d);
            } else {
                //Sequence reader
                List<Collection<Collection<Writable>>> list = nextSeqRRVals.get(d.readerName);
                Pair<INDArray,INDArray> p = convertWritablesSequence(list,minExamples,longestTS,d,longestSequence);
                inputArrs[i] = p.getFirst();
                inputArrMasks[i] = p.getSecond();
                if(inputArrMasks[i] != null) inputMasks = true;
            }
        }
        if(!inputMasks) inputArrMasks = null;


        //Third: create the outputs
        INDArray[] outputArrs = new INDArray[outputs.size()];
        INDArray[] outputArrMasks = new INDArray[outputs.size()];
        boolean outputMasks = false;
        i=0;
        for(SubsetDetails d : outputs){
            if(nextRRVals.containsKey(d.readerName)) {
                //Standard reader
                List<Collection<Writable>> list = nextRRVals.get(d.readerName);
                outputArrs[i] = convertWritables(list,minExamples,d);
            } else {
                //Sequence reader
                List<Collection<Collection<Writable>>> list = nextSeqRRVals.get(d.readerName);
                Pair<INDArray,INDArray> p = convertWritablesSequence(list,minExamples,longestTS,d,longestSequence);
                outputArrs[i] = p.getFirst();
                outputArrMasks[i] = p.getSecond();
                if(outputArrMasks[i] != null) outputMasks = true;
            }
        }
        if(!outputMasks) outputArrMasks = null;

        return new org.nd4j.linalg.dataset.MultiDataSet(inputArrs,outputArrs,inputArrMasks,outputArrMasks);
    }

    private INDArray convertWritables(List<Collection<Writable>> list, int minValues, SubsetDetails details ){
        INDArray arr;
        if(details.entireReader) arr = Nd4j.create(minValues, list.get(0).size());
        else if(details.oneHot) arr = Nd4j.zeros(minValues, details.oneHotNumClasses);
        else arr = Nd4j.create(minValues, details.subsetEndInclusive-details.subsetStart + 1);

        int[] idx = new int[2];
        for( int i=0; i<minValues; i++){
            idx[0] = i;
            Collection<Writable> c = list.get(i);
            if(details.entireReader) {
                //Convert entire reader contents, without modification
                int j = 0;
                for (Writable w : c) {
                    idx[1] = j++;
                    arr.putScalar(idx, w.toDouble());
                }
            } else if(details.oneHot){
                //Convert a single column to a one-hot representation
                Writable w = null;
                if(c instanceof List) w = ((List<Writable>)c).get(details.subsetStart);
                else{
                    Iterator<Writable> iter = c.iterator();
                    for( int k=0; k<=details.subsetStart; k++ ) w = iter.next();
                }
                idx[1] = w.toInt();     //Index of class
                arr.putScalar(idx,1.0);
            } else {
                //Convert a subset of the columns
                Iterator<Writable> iter = c.iterator();
                for( int j=0; j<details.subsetStart; j++ ) iter.next();
                int k=0;
                for( int j=details.subsetStart; j<=details.subsetEndInclusive; j++){
                    idx[1] = k++;
                    arr.putScalar(idx,iter.next().toDouble());
                }
            }
        }

        return arr;
    }

    /** Convert the writables to a sequence (3d) data set, and also return the mask array (if necessary) */
    private Pair<INDArray,INDArray> convertWritablesSequence(List<Collection<Collection<Writable>>> list, int minValues,
                                                             int maxTSLength, SubsetDetails details, int[] longestSequence ){
        if(maxTSLength == -1) maxTSLength = list.size();
        INDArray arr;
        if(details.entireReader) arr = Nd4j.create(minValues,list.get(0).size(),maxTSLength);
        else if(details.oneHot) arr = Nd4j.create(minValues,details.oneHotNumClasses,maxTSLength);
        else arr = Nd4j.create(minValues,details.subsetEndInclusive-details.subsetStart+1,maxTSLength);

        boolean needMaskArray = false;
        for( Collection<Collection<Writable>> c : list ){
            if(c.size() < maxTSLength ) needMaskArray = true;
        }
        INDArray maskArray;
        if(needMaskArray) maskArray = Nd4j.ones(minValues,maxTSLength);
        else maskArray = null;


        int[] idx = new int[3];
        int[] maskIdx = new int[2];
        for( int i=0; i<minValues; i++ ){
            idx[0] = i;
            Collection<Collection<Writable>> sequence = list.get(i);

            //Offset for alignment:
            int startOffset;
            if(alignmentMode == AlignmentMode.ALIGN_START || alignmentMode == AlignmentMode.EQUAL_LENGTH) {
                startOffset = 0;
            } else {
                //Align end
                //Only practical differences here are: (a) offset, and (b) masking
                startOffset = longestSequence[i] - sequence.size();
            }

            int t=0;
            for (Collection<Writable> timeStep : sequence) {
                idx[2] = startOffset + t++;

                if(details.entireReader) {
                    //Convert entire reader contents, without modification
                    Iterator<Writable> iter = timeStep.iterator();
                    int j = 0;
                    while (iter.hasNext()) {
                        idx[1] = j++;
                        arr.putScalar(idx,iter.next().toDouble());
                    }
                } else if(details.oneHot){
                    //Convert a single column to a one-hot representation
                    Writable w = null;
                    if(timeStep instanceof List) w = ((List<Writable>)timeStep).get(details.subsetStart);
                    else{
                        Iterator<Writable> iter = timeStep.iterator();
                        for( int k=0; k<=details.subsetStart; k++ ) w = iter.next();
                    }
                    int classIdx = w.toInt();
                    idx[1] = classIdx;
                    arr.putScalar(idx,1.0);
                } else {
                    //Convert a subset of the columns...
                    Iterator<Writable> iter = timeStep.iterator();
                    for( int j=0; j<details.subsetStart; j++ ) iter.next();
                    int k=0;
                    for( int j=details.subsetStart; j<=details.subsetEndInclusive; j++){
                        idx[1] = k++;
                        arr.putScalar(idx,iter.next().toDouble());
                    }
                }
            }

            //For any remaining time steps: set mask array to 0 (just padding)
            if(needMaskArray){
                maskIdx[0] = i;
                //Masking array entries at start (for align end)
                if(alignmentMode == AlignmentMode.ALIGN_END) {
                    for (int t2 = 0; t2 < startOffset; t2++) {
                        maskIdx[1] = t2;
                        maskArray.putScalar(maskIdx, 0.0);
                    }
                }

                //Masking array entries at end (for align start)
                if(alignmentMode == AlignmentMode.ALIGN_START) {
                    for (int t2 = t; t2 < maxTSLength; t2++) {
                        maskIdx[1] = t2;
                        maskArray.putScalar(maskIdx, 0.0);
                    }
                }
            }


        }


        return new Pair<>(arr,maskArray);
    }

    @Override
    public void setPreProcessor(MultiDataSetPreProcessor preProcessor) {
        this.preProcessor = preProcessor;
    }

    @Override
    public boolean hasNext() {
        for(RecordReader rr : recordReaders.values()) if(!rr.hasNext()) return false;
        for(SequenceRecordReader rr : sequenceRecordReaders.values()) if(!rr.hasNext()) return false;
        return true;
    }


    public static class Builder {

        private int batchSize;
        private AlignmentMode alignmentMode = AlignmentMode.EQUAL_LENGTH;
        private Map<String,RecordReader> recordReaders = new HashMap<>();
        private Map<String,SequenceRecordReader> sequenceRecordReaders = new HashMap<>();

        private List<SubsetDetails> inputs = new ArrayList<>();
        private List<SubsetDetails> outputs = new ArrayList<>();


        public Builder(int batchSize){
            this.batchSize = batchSize;
        }

        public void addReader(String readerName, RecordReader recordReader){
            recordReaders.put(readerName,recordReader);
        }

        public void addSequenceReader(String seqReaderName, SequenceRecordReader seqRecordReader){
            sequenceRecordReaders.put(seqReaderName,seqRecordReader);
        }

        /** Set the sequence alignment mode for all sequences */
        public void setSequenceAlignmentMode(AlignmentMode alignmentMode){
            this.alignmentMode = alignmentMode;
        }

        /** Set as an input, the entire contents (all columns) of the RecordReader or SequenceRecordReader */
        public void addInput(String readerName){
            inputs.add(new SubsetDetails(readerName,true,false,-1,-1,-1));
        }

        /** Set as an input, a subset of the specified RecordReader or SequenceRecordReader
         * @param readerName Name of the reader
         * @param columnFirst First column index, inclusive
         * @param columnLast Last column index, inclusive
         */
        public void addInput(String readerName, int columnFirst, int columnLast ){
            inputs.add(new SubsetDetails(readerName,false,false,-1,columnFirst,columnLast));
        }

        /** Add as an input a single column from the specified RecordReader / SequenceRecordReader
         * The assumption is that the specified column contains integer values in range 0..numClasses-1;
         * this integer will be converted to a one-hot representation
         * @param readerName
         * @param column
         * @param numClasses
         */
        public void addInputOneHot(String readerName, int column, int numClasses){
            inputs.add(new SubsetDetails(readerName,false,true,numClasses,column,-1));
        }

        /** Set as an output, the entire contents (all columns) of the RecordReader or SequenceRecordReader */
        public void addOutput(String readerName){
            outputs.add(new SubsetDetails(readerName,true,false,-1,-1,-1));
        }

        /**
         * @param readerName Name of the reader
         * @param columnFirst First column index
         * @param columnLast Last column index (inclusive)
         */
        public void addOutput(String readerName, int columnFirst, int columnLast){
            outputs.add(new SubsetDetails(readerName,false,false,-1,columnFirst,columnLast));
        }

        /** An an output, where the output is taken from a single column from the specified RecordReader / SequenceRecordReader
         * The assumption is that the specified column contains integer values in range 0..numClasses-1;
         * this integer will be converted to a one-hot representation (usually for classification)
         * @param readerName Name of the RecordReader / SequenceRecordReader
         * @param column index of the column
         * @param numClasses Number of classes
         */
        public void addOutputOneHot(String readerName, int column, int numClasses ){
            outputs.add(new SubsetDetails(readerName,false,true,numClasses,column,-1));
        }

        public RecordReaderMultiDataSetIterator build(){
            return new RecordReaderMultiDataSetIterator(this);
        }
    }

    @AllArgsConstructor
    private static class SubsetDetails {
        private final String readerName;
        private final boolean entireReader;
        private final boolean oneHot;
        private final int oneHotNumClasses;
        private final int subsetStart;
        private final int subsetEndInclusive;
    }
}
