package weka.custom_classifier.J48;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;

import com.google.common.math.DoubleMath;
import java.util.Scanner;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.Capabilities.Capability;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.custom_classifier.Tree;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;

/**
 * Custom J48
 * Using binary split for numeric attributes handling
 * Using subtree raising for pruning algorithm, no confidence factor needed.
 * ^it is based on the idea of rule-based pruning model, trying to remove conjuction
 *
 */
public class CustomJ48 extends Classifier
{
	/** For serialization */
	private static final long serialVersionUID = 1L;
	
	private Tree decisionTree;
	private ArrayList<Integer> infoBinarySplit; //variable that holds threshold for numeric attributes in binary splitting for numeric attribute
        private ArrayList< ArrayList<Double> > infoMultiSplit; //variable that holds threshold for numeric attributes in multi splitting for numeric attribute
	private ArrayList <ArrayList<Integer>> largestAttributeDistribution;
        private int binaryNumericSplittingOption; //default use binary splitting
        private int numberOfMultiSplit; //default 10
	
	/**
	 * Default Constructor
	 */
	public CustomJ48()
	{
        decisionTree = new Tree();
        infoBinarySplit = new ArrayList();
        infoMultiSplit = new ArrayList();
        largestAttributeDistribution = new ArrayList();
        binaryNumericSplittingOption = 1;
        numberOfMultiSplit = 10;
	}
	
	/**
	 * @return capabilities
	 */
	private Capabilities classifierCapabilities(){
        Capabilities capabilities = super.getCapabilities();

        // attributes
        capabilities.enable(Capability.NOMINAL_ATTRIBUTES);
        capabilities.enable(Capability.NUMERIC_ATTRIBUTES);
        capabilities.enable(Capability.MISSING_VALUES);

        // class
        capabilities.enable(Capability.NOMINAL_CLASS);

        // instances
        capabilities.setMinimumNumberInstances(0);

        return capabilities;
	}
        
    /**
     * 
     * @param binarySplittingForNumeric 1 if true (use binary split), 0 to use multisplit
     * @param numerOfMultiSplit the number of threshold classes for multisplit, default 10, minimum 3
     */
    private void setOption(int binarySplittingForNumeric, int numberOfMultiSplit)
    {
        binaryNumericSplittingOption = binarySplittingForNumeric;
        this.numberOfMultiSplit = numberOfMultiSplit;
    }
	
	/**
	 * Build J48 classifier
	 * @param data training data
	 */
	public void buildClassifier(Instances data) throws Exception 
	{
        classifierCapabilities().testWithFail(data);

        data.deleteWithMissingClass(); //deal with missing class

        if (binaryNumericSplittingOption==1)
            data = new Instances(binarySplitNumericAttribute(data)); //handle numeric attributes using binary split
        else //multisplit
            data = new Instances(multiSplitNumericAttribute(data));
          
        ArrayList<Attribute> selectedAttr = new ArrayList();
        generateTree(data, data, decisionTree, selectedAttr);

        //post-prune
        pruneTree(null, null, decisionTree, data);

        //accuracy
        System.out.println("Model accuracy = "+accuracyPerformance(data));
	}
		
	/**
	 * Split attributes that has numeric value using binary split
	 * @param data training data
	 * @return Instances (processed) : numeric -> nominal with binary splitting
	 * @throws Exception
	 */
	private Instances binarySplitNumericAttribute(Instances data) throws Exception
	{
        Instances retVal = new Instances(data);

        int len = data.numAttributes();
        int count0=0;
        int count1=0;
        for (int i=0; i<len; i++)
        {
            if (data.attribute(i).isNumeric()) 
            {
                retVal.deleteAttributeAt(i);

                double valMin = Double.MAX_VALUE;
                double valMax = Double.MIN_VALUE;
                for (int j=0; j<data.numInstances(); j++)
                {
                    if (data.instance(j).value(i) > valMax)
                    {
                        valMax = data.instance(j).value(i);
                    }
                    if (data.instance(j).value(i) < valMin)
                    {
                        valMin = data.instance(j).value(i);
                    }
                }
                int middleValue = (int) Math.floor((valMax-valMin)/2 + valMin); //threshold calculation
                infoBinarySplit.add(middleValue);

                //modify training data to fit the rule
                Add filter = new Add();
                filter.setAttributeIndex("first");
                filter.setNominalLabels("moreThan"+Integer.toString(middleValue)+", lessEqualThan"+Integer.toString(middleValue));
                filter.setAttributeName(data.attribute(i).name());
                filter.setInputFormat(retVal);
                retVal = Filter.useFilter(retVal, filter);

                for (int j=0; j<data.numInstances(); j++)
                {
                    if (!data.instance(j).isMissing(i))
                    {		
                        if (Double.compare(data.instance(j).value(i), middleValue) > 0) {
                            retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), 0);
                            count0++;
                        }
                        else if (Double.compare(data.instance(j).value(i), middleValue) <= 0)  {
                            retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), 1);
                            count1++;
                        }
                    }
                    //missing value handling on another procedure
                }
            }
        }
        return retVal;
	}
        
        /**
	 * Split attributes that has numeric value using multi-threshold split
	 * @param data training data
	 * @return Instances (processed) : numeric -> nominal with binary splitting
	 * @throws Exception
	 */
	private Instances multiSplitNumericAttribute(Instances data) throws Exception
	{
        Instances retVal = new Instances(data);

        int len = data.numAttributes();
        int count0=0;
        int count1=0;
        for (int i=0; i<len; i++)
        {
            if (data.attribute(i).isNumeric()) 
            {
                retVal.deleteAttributeAt(i);

                double valMin = Double.MAX_VALUE;
                double valMax = Double.MIN_VALUE;
                for (int j=0; j<data.numInstances(); j++)
                {
                    if (data.instance(j).value(i) > valMax)
                    {
                        valMax = data.instance(j).value(i);
                    }
                    if (data.instance(j).value(i) < valMin)
                    {
                        valMin = data.instance(j).value(i);
                    }
                }
                double thresholdGap = ((valMax-valMin)/(this.numberOfMultiSplit-2));
                ArrayList<Double> infoAttrSplit = new ArrayList();
                String nominalLabels = "";
                for (int x=0; x<numberOfMultiSplit-1; x++)
                {
                    infoAttrSplit.add((valMin)+(thresholdGap*x));
                    if (x>0)
                        nominalLabels += ", ";
                    nominalLabels += "<="+Double.toString(infoAttrSplit.get(x));
                }
                nominalLabels += ", ";
                nominalLabels += ">"+Double.toString(infoAttrSplit.get(infoAttrSplit.size()-1));
                infoMultiSplit.add(infoAttrSplit);

                //modify training data to fit the rule
                Add filter = new Add();
                filter.setAttributeIndex("first");
                filter.setNominalLabels(nominalLabels);
                filter.setAttributeName(data.attribute(i).name());
                filter.setInputFormat(retVal);
                retVal = Filter.useFilter(retVal, filter);

                for (int j=0; j<data.numInstances(); j++)
                {
                    if (!data.instance(j).isMissing(i))
                    {		
                        boolean found = false;
                        for (int x=0; x<infoAttrSplit.size() && !found; x++)
                        {
                            if (Double.compare(data.instance(j).value(i),infoAttrSplit.get(x)) <= 0)
                            {
                                retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), x);
                                found = true;
                            }
                        }
                        if (!found) //more than last value
                        {
                            retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), infoAttrSplit.size());
                        }
                    }
                    //missing value handling on another procedure
                }
            }
        }
        return retVal;
	}
	
	/**
	 * Generate most common attribute values distribution over class
	 * @param data training data
	 */
	public void generateAttributesValueDistribution(Instances data)
	{
        int numAttr = data.numAttributes();
        int maxAttr = Integer.MIN_VALUE;
        int simpan = 0;
        for (int i=0; i<numAttr; i++)
            if (data.attribute(i).numValues() > maxAttr) {
                maxAttr = data.attribute(i).numValues();
                simpan = i;
            }
        int[][][] counter = new int[data.numClasses()][data.numAttributes()][data.attribute(simpan).numValues()];

        //Counting matrix of largest attribute value distribution over classes
        for (int i=0; i<data.numInstances(); i++)
        {
            for (int j=0; j<data.numAttributes(); j++) {
                if (!data.instance(i).isMissing(j)) {
                    counter[(int) data.instance(i).classValue()][j][(int) data.instance(i).value(data.instance(i).attribute(j))]++;
                }
            }
        }

        //Generating matrix of largest attribute value distribution over classes
        for (int i=0; i<data.numClasses(); i++) {
            ArrayList<Integer> maxAttributeIdx = new ArrayList<Integer>();
            for (int j=0; j<data.numAttributes(); j++) {
                int max = Integer.MIN_VALUE;
                simpan = 0;
                for (int k=0; k<maxAttr; k++)
                    if (counter[i][j][k] > max) {
                        max = (int) counter[i][j][k];
                        simpan = k;
                    }
                maxAttributeIdx.add(simpan);
            }
            largestAttributeDistribution.add(maxAttributeIdx);
        }		
	}
	
	/**
	 * Replace instances with missing value
	 * @param data Instances
	 * @return Instances with missing value replaced
	 */
	public Instances replaceMissingAttribute(Instances data)
	{
        Instances retVal = new Instances(data);
        for (int i=0; i<data.numInstances(); i++) 
        {
            for (int j=0; j<data.instance(i).numAttributes(); j++) {
                if (data.instance(i).isMissing(j))
                {
                    retVal.instance(i).setValue(j, largestAttributeDistribution.get(data.instance(i).classIndex()).get(j));
                }
            }
        }
        return retVal;
	}
	
	/**
	 * Replace attribute of instance with missing value, using most common attribute
	 * @param data Instances
	 * @return Instances with missing value replaced
	 */
	public Instance replaceMissingAttribute(Instance data)
	{
        Instance retVal = new Instance(data);
        for (int j=0; j<data.numAttributes(); j++) {
            if (data.isMissing(j))
            {
                retVal.setValue(j, largestAttributeDistribution.get(data.classIndex()).get(j));
            }
        }
        return retVal;
	}
	
	/**
	 * Split numeric attribute for supplied test set using binary split
	 * @param data test set
	 * @return Instances (processed): numeric -> nominal
	 * @throws Exception
	 */
	public Instances binarySplitNumericSuppliedTest(Instances data) throws Exception
	{
        Instances retVal = new Instances(data);
        int count = 0;
        int len = data.numAttributes();
        for (int i=0; i<len; i++)
        {
            if (data.attribute(i).isNumeric()) 
            {			
                retVal.deleteAttributeAt(i);

                Add filter = new Add();
                filter.setAttributeIndex("first");
                filter.setNominalLabels("moreThan"+Integer.toString(infoBinarySplit.get(count))+", lessEqualThan"+Double.toString(infoBinarySplit.get(count)));
                filter.setAttributeName(data.attribute(i).name());
                filter.setInputFormat(retVal);
                retVal = Filter.useFilter(retVal, filter);

                for (int j=0; j<data.numInstances(); j++)
                {
                    if (Double.compare(data.instance(j).value(i), infoBinarySplit.get(count)) > 0) {
                        retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), 0);
                    }
                    else {
                        retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), 1);
                    }
                }
                count+=1;
            }
        }
        return retVal;
	}
        
    /**
     * Split numeric attribute for supplied test set using multi split
     * @param data test set
     * @return Instances (processed): numeric -> nominal
     * @throws Exception 
     */
    public Instances multiSplitNumericSuppliedTest(Instances data) throws Exception
	{
        Instances retVal = new Instances(data);
        int count = 0;
        int len = data.numAttributes();
        for (int i=0; i<len; i++)
        {
            if (data.attribute(i).isNumeric()) 
            {			
                retVal.deleteAttributeAt(i);

                Add filter = new Add();
                filter.setAttributeIndex("first");
                String nominalLabels = "";
                for (int x=0; x<=infoMultiSplit.get(count).size(); x++)
                {
                     nominalLabels += "<="+Double.toString(infoMultiSplit.get(count).get(x));
                }
                nominalLabels += ">"+Double.toString(infoMultiSplit.get(count).get(infoMultiSplit.get(count).size()-1));
                
                filter.setNominalLabels(nominalLabels);
                filter.setAttributeName(data.attribute(i).name());
                filter.setInputFormat(retVal);
                retVal = Filter.useFilter(retVal, filter);

                for (int j=0; j<data.numInstances(); j++)
                {
                    boolean found = false;
                    for (int x=0; x<infoMultiSplit.get(count).size() && !found; x++)
                    {
                        if (Double.compare(data.instance(j).value(i),infoMultiSplit.get(count).get(x)) <= 0)
                        {
                            retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), x);
                            found = true;
                        }
                    }
                    if (!found) //more than last value
                    {
                        retVal.instance(j).setValue(retVal.attribute(data.attribute(i).name()), infoMultiSplit.get(count).size());
                    }
                }
                count+=1;
            }
        }
        return retVal;
	}
	
	/**
	 * Prune using subtree raising method
	 * @param grandparent a node
	 * @param parent a node
	 * @param child a node
	 */
	public void pruneTree (Tree grandparent, Tree parent, Tree child, Instances data) 
	{
        boolean found = true;
        if (parent!=null) //safety measure
        {
            found = false;
            Tree[] childList = parent.getChildren();
            for (int i=0; i<childList.length && !found; i++) 
            {
                if (child.equals(childList[i]))
                {
                    found = true;
                }
            }
        }
        if (found) 
        {
            if (child.getAttribute() == null) //at leaf
            {
                subTreeRaising(grandparent, parent, child, data);
            }
            else { //not at leaf
                Tree[] childList = child.getChildren();
                for (int i=0; i<childList.length; i++)
                {
                    pruneTree(parent, child, childList[i], data);
                }

                if (grandparent!=null && parent!=null && child!=null) //try subtree raising
                {
                    subTreeRaising(grandparent, parent, child, data);
                }
            }
        }
	}
	
	/**
	 * Subtree raising test if the subtree raising makes performance better
	 * @param grandparent
	 * @param parent
	 * @param child
	 * @param data set for performance testing
	 */
	private void subTreeRaising(Tree grandparent, Tree parent, Tree child, Instances data)
	{
        if (grandparent!=null && parent!=null && child!=null) 
        {
            Tree[] childList = grandparent.getChildren();
            boolean found = false;
            int idx = 0;
            for (int i=0; i<childList.length && !found; i++)
            {
                if (childList[i].getAttribute()==parent.getAttribute())
                {
                    found = true;
                    idx = i;
                }
            }

            double accuracy1 = this.accuracyPerformance(data);
            //change parent node using child node
            grandparent.addChild(idx, child);
            //test performance, if worse then back to before
            double accuracy2 = this.accuracyPerformance(data);
            if (accuracy1>=accuracy2) //revert if the performance worse
            {
                grandparent.addChild(idx, parent);
            }
        }
	}
	
	/**
	 * Measure accuracy performance
	 * @param data datatest for accuracy measurement
	 * @return
	 */
	private double accuracyPerformance(Instances data)
	{
        double count = 0.0;
        for (int i=0; i<(int)data.numInstances(); i++)
        {
            double clsVal = data.instance(i).classValue();
            double clsFly = classifyInstance(data.instance(i), this.decisionTree);
            if (clsFly==clsVal)
                count += 1.0;
        }
        return count / (double)data.numInstances();
	}
	
	/**
	 * Classify instance
	 */
	public double classifyInstance(Instance instance){
        return classifyInstance(instance, decisionTree);
	}
	
	/**
	 * Classify instance at specific node
	 * @param instance
	 * @param tree
	 * @return class
	 */
	private double classifyInstance(Instance instance, Tree tree)
	{
        //instance = replaceMissingAttribute(instance);
        if(tree.getAttribute() == null){
            return tree.getClassValue();
        }else{
            if (instance.isMissing(tree.getAttribute())) 
            {
                return classifyInstance(instance, tree.getChild(this.maxIndex(tree.getProbs())));
            }
            else
                return classifyInstance(instance, tree.getChild((int) instance.value(tree.getAttribute())));
        }
	}
        
    /**
     * Classify instances - for supplied test set
     * @param data instances
     * @return double, classes for each instance
     * @throws Exception 
     */
    public ArrayList<Double> classifyInstances(Instances data) throws Exception
    {
        data.deleteWithMissingClass(); //deal with missing class

        if (binaryNumericSplittingOption==1)
            data = new Instances(binarySplitNumericAttribute(data)); //handle numeric attributes using binary split
        else //multisplit
            data = new Instances(multiSplitNumericAttribute(data));
        generateAttributesValueDistribution(data);
        data = new Instances(replaceMissingAttribute(data)); //handles attributes that has missing value
        
        ArrayList<Double> retVal = new ArrayList();
        for (int i=0; i<data.numInstances(); i++)
        {
            retVal.add(classifyInstance(data.instance(i)));
        }
        
        return retVal;
    }
	
	private double calculateNonMissingValueDistribution(Instances data, Attribute att){
		double count = 0.0;
		Enumeration instances = data.enumerateInstances();
		
		while(instances.hasMoreElements()){
			Instance inst = (Instance) instances.nextElement();
			if(inst.value(att) != Instance.missingValue()){
				count++;
			}
		}

		return count;		
	}
    
	/**
	 * Build J48 tree
	 * @param data training data, no missing value
	 * @param tree
         * @param selectedAttr already selected attribute at parents of the node
	 */
	public void generateTree(Instances data, Instances parentData, Tree tree, ArrayList<Attribute> selectedAttr)
	{
        Enumeration attributes = data.enumerateAttributes();
        double[] gainRatio = new double[data.numAttributes()];

        //gain ratio calculation
        while(attributes.hasMoreElements())
        {
            Attribute attribute = (Attribute) attributes.nextElement();
            if (!selectedAttr.contains(attribute)) {
            
                double infoGain = informationGain(data, attribute);
                double splitInfo = splitInfo(data);
                if (Double.compare(splitInfo, 0.0)!=0 && !Double.isNaN(infoGain)) {
                	double numNonMissingValue = calculateNonMissingValueDistribution(data, attribute);
                	double numInstances = data.numInstances();
                	double timesFactor = numNonMissingValue / numInstances;
                	
                	if (Double.compare(numNonMissingValue,0.0) == 0 || Double.compare(numInstances,0.0) == 0) {
                		timesFactor = 0.0;
                	}
                    gainRatio[attribute.index()] = timesFactor * (infoGain / splitInfo);
                }
                else if (!Double.isNaN(infoGain))
                    gainRatio[attribute.index()] =infoGain;
                else
                	gainRatio[attribute.index()] = 0.0;
            }
            else
                gainRatio[attribute.index()] = 0.0;
        }
        
        Attribute highestIGAtt = data.attribute(maxIndex(gainRatio));
        
        //build decision tree
        tree.setAttribute(highestIGAtt);

        if(Double.compare(gainRatio[highestIGAtt.index()], 0.0) == 0) //at leaf
        {
            tree.setAttribute(null);
            if (data.numInstances()!=0) {              
                double[] distribution = new double[data.numClasses()];
                Enumeration instances = data.enumerateInstances();

                while(instances.hasMoreElements())
                {
                    Instance instance = (Instance) instances.nextElement();
                    distribution[(int) instance.classValue()]++;
                }

                tree.setClassValue(maxIndex(distribution));
            }
            else //penanganan example kosong
            {
                tree.setClassValue(dominantClasses(parentData));
            }
            tree.setClassAttribute(data.classAttribute());
        }
        else //not at leaf, build the children
        {
            selectedAttr.add(highestIGAtt);
            Instances[] splittedData = split(data, highestIGAtt);
       
            Tree[] children = new Tree[tree.getAttribute().numValues()];
            double[] probs = new double[tree.getAttribute().numValues()];
            
            for (int i=0; i<probs.length; i++)
            {
                probs[i] = (double) splittedData[i].numInstances() / (double) data.numInstances();
            }
            tree.addProbs(probs);

            for(int i = 0; i < children.length; i++)
            {
                children[i] = new Tree();
                tree.addChildren(children);
                generateTree(splittedData[i], data, children[i], selectedAttr);
            }
        }
	}
	
	/**
	 * return class value of dominant classes of training data
	 * @param data training data
	 * @return class value
	 */
	public double dominantClasses(Instances data){
		Enumeration instances = data.enumerateInstances();
		double[] classValueCount = new double[data.classAttribute().numValues()];
		
		while (instances.hasMoreElements()) {
			Instance inst = (Instance) instances.nextElement();
			classValueCount[(int) inst.classValue()]++;
		}
		
		return (double) maxIndex(classValueCount);
	}
	
	/**
	 * Split instances
	 * @param data
	 * @param att
	 * @return splitted instances
	 */
	private Instances[] split(Instances data, Attribute att)
	{
        Instances[] splittedData = new Instances[att.numValues()];

        for(int i = 0; i < splittedData.length; i++)
        {
            splittedData[i] = new Instances(data, data.numInstances());
            splittedData[i].delete();
        }

        for(int i = 0; i < data.numInstances(); i++)
        {			
            splittedData[(int) data.instance(i).value(att)].add(data.instance(i));
        }

        return splittedData;
	}
	
	/**
	 * @param array
	 * @return array's index which hold highest value 
	 */
	private int maxIndex(double[] array){
        int maxIndex = 0;

        for (int i = 1; i < array.length; i++){
            double newnumber = array[i];
            if ((newnumber > array[maxIndex])){
                maxIndex = i;
            }
        }

        return maxIndex;
	}
	
	/**
	 * Calculate information gain
	 * @param data training data
	 * @param att
	 * @return information gain
	 */
	private double informationGain(Instances data, Attribute att)
	{
        double informationGain = entropy(data);
        int numOfLabels = att.numValues();
        
        Instances[] instancesDistribution = new Instances[numOfLabels];

        for(int i = 0; i < instancesDistribution.length; i++)
        {
            instancesDistribution[i] = new Instances(data, data.numInstances());
            instancesDistribution[i].delete();
        }
 
        for(int i = 0; i < data.numInstances(); i++) 
        {
        	if (!data.instance(i).isMissing(att))
        		instancesDistribution[(int) data.instance(i).value(att)].add(data.instance(i));
        }
        
        for(int i = 0; i < numOfLabels; i++)
        {
            double numInstancesOfLabel = (double) instancesDistribution[i].numInstances();
            informationGain -=  numInstancesOfLabel / (double) data.numInstances() * entropy(instancesDistribution[i]);
        }
        
        return informationGain;
	}
	
	/**
	 * Split info calculation
	 * @param data
	 * @return splt info
	 */
	private double splitInfo(Instances data)
	{
        double splitInfo = 0.0;
        int[]  distribution = new int[data.numClasses()];

        for(int i = 0; i < data.numInstances(); i++)
        {
            distribution[(int) data.instance(i).classValue()]++;
        }

        for(int i = 0; i < data.numClasses(); i++)
        {
            double temp = (double) distribution[i] / (double) data.numInstances(); 

            if(Double.compare(temp, 0) != 0)
                splitInfo += (temp * DoubleMath.log2(temp));
        }

        return Math.abs(splitInfo);
    }
	
	/**
	 * Entropy calculation
	 * @param data instances
	 * @return entropy
	 */
	private double entropy(Instances data){
        double entropy = 0.0;
        int numOfClasses = data.classAttribute().numValues();
        int[] numOfInstancesPerClass = new int[numOfClasses];

        for(int i = 0; i < data.numInstances(); i++)
        {
            numOfInstancesPerClass[(int) data.instance(i).classValue()]++;
        }

        for(int i = 0; i < numOfClasses; i++)
        {
            if(numOfInstancesPerClass[i] != 0)
            {
                double temp = (double) numOfInstancesPerClass[i] / (double) data.numInstances();
                entropy -= temp * DoubleMath.log2(temp);
            }
        }

        return entropy;
	}
	
	/**
	 * Convert tree to string
	 * @param level
	 * @param tree
	 * @return
	 */
	private String toString(int level, Tree tree) 
	{
		StringBuffer text = new StringBuffer();
		
		if (tree.getAttribute() == null) 
		{
			if (Instance.isMissingValue(tree.getClassValue())) 
			{
				text.append(": null");
				} 
			else 
			{
				text.append(": " + tree.getClassAttribute().value((int) tree.getClassValue()));
			} 
		} 
		else 
		{
			for (int j = 0; j < tree.getAttribute().numValues(); j++) 
			{
				text.append("\n");
				for (int i = 0; i < level; i++) 
				{
					text.append("|  ");
				}
				text.append(tree.getAttribute().name() + " = " + tree.getAttribute().value(j));
				text.append(toString(level + 1, tree.getChild(j)));
			}
		}
		return text.toString();
	}
	
	/**
	 * Convert to string
	 */
	public String toString() 
	{
		if ((decisionTree.getAttribute() == null) && (decisionTree.getChildren() == null)) {
			return "J48: No model built yet.";
	    }
	    	return "J48\n\n" + toString(0, decisionTree);
        }
	
	/**
	 * For testing purpose
	 * @param filePath
	 * @return
	 * @throws IOException
	 */
	public static Instances loadDatasetArff(String filePath) throws IOException
    { 
        ArffLoader loader = new ArffLoader();
        loader.setSource(new File(filePath));
        return loader.getDataSet();
    }
	
	/**
	 * For testing purpose
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception
	{
        String dataset = "example/cpu.arff";
        CustomJ48 j48 = new CustomJ48();
        Instances data = CustomJ48.loadDatasetArff(dataset);

        data.setClass(data.attribute(data.numAttributes() - 1));

        System.out.println("Custom made J48");
        j48.setOption(0, 10);
        j48.buildClassifier(data);
        System.out.println(j48);


        J48 tree = new J48();
        tree.buildClassifier(data);
        Evaluation eval = new Evaluation(data);
        eval.evaluateModel(tree, data);
        System.out.println("Weka's J48");
        System.out.println(tree);
        System.out.println(eval.toSummaryString());
	}
}
