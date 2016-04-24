package MyTextClassify;

import jeasy.analysis.MMAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.StringHelper;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.*;
import java.sql.*;
import java.util.*;

public class BeyesClassification1 {
	IKAnalyzer analyzer = null;
	private String label = null;
	private long trainTime = 0;
	public String[] labelsName = null;
	public ArrayList<Label> labels = null;
	public Set<String> vocabulary = new HashSet<String>();
	public String trainPath = null;
	public String testPath = null;
	public HashSet<String> dic= null;
	BeyesClassification1() {
		analyzer = new IKAnalyzer();
		dic=new HashSet<String>();
		String tempString=null;
		String sql = "select * from dic ;";
		String url="jdbc:mysql://localhost:3306/first?user=root&password=root&useUnicode=true&characterEncoding=UTF-8";
		ResultSet resultSet=null;
		try {
			Driver driver=new com.mysql.jdbc.Driver();
			Connection conn = DriverManager.getConnection(url);
			Statement statement = conn.createStatement();
			resultSet = statement.executeQuery(sql);
			while (resultSet.next())
				dic.add(resultSet.getString("word").toString());
		}catch (SQLException e) {
			e.printStackTrace();
		}
	}
	public int findMax(double[] values) {
		int index = 0;
		int numberOfZero=0;
		for (int i = 0; i < values.length; i++) {
			if (values[i] > values[index])
				index = i;
			if(values[i]==0)
				numberOfZero++;
		}
		if(numberOfZero==values.length)
			return -1;
		return index;
	}

	public void sort(String[] Date, int left, int right) {//从小到大快速排序
		if (left >= right)
			return;
		int index = right;
		String middle = Date[index];
		for (int i = left, j = right; i < j;) {
			while (Date[i].compareTo(middle) <= 0 && i < j)
				i++;
			Date[index] = Date[i];
			index = i;
			while (Date[j].compareTo(middle) >= 0 && i < j)
				j--;
			Date[index] = Date[j];
			index = j;
		}
		if(Date[index]==middle)///解决最差情况下的快速排序问题
			for(int i=left;i<=right-1;i++)
				if(Date[i].compareTo(Date[i+1])<=0){
					if(i==right-1)
						return;
				}else
					break;			
		Date[index] = middle;
		sort(Date, left, index - 1);
		sort(Date, index + 1, right);
	}

	ArrayList<String> readFile(String fileName) throws IOException, FileNotFoundException {
		File f=new File(fileName);  //文件获取
		InputStreamReader isr=new InputStreamReader(new FileInputStream(f),"GBK");//utf8格式读取文件
		char[] cbuf=new char[(int) f.length()];  //全文件装入内存
		isr.read(cbuf); //读
		//Analyzer analyzer=new MMAnalyzer();  //分词器
		TokenStream tokens=analyzer.tokenStream("Contents", new StringReader(new String(cbuf)));
		Token token=null;
		ArrayList<String> v=new ArrayList<String>();
		while (tokens.incrementToken()) {
			CharTermAttribute charTermAttribute = tokens.getAttribute(CharTermAttribute.class);
			String string=charTermAttribute.toString();
			if (dic.contains(string))
				v.add(string);
		}
		/*查看分词结果
		FileOutputStream a=new FileOutputStream(new File("out"),true);
		PrintWriter out=new PrintWriter(a);
		Iterator<String> iterator=v.iterator();
		while (iterator.hasNext())
			a.write((iterator.next()+" ").getBytes());
		a.write("\n".getBytes());
		*/

		return v;
	}
	ArrayList<String> readArticle(String in) throws IOException {
		TokenStream tokens=analyzer.tokenStream("Contents", new StringReader(in));
		ArrayList<String> v=new ArrayList<String>();
		while (tokens.incrementToken()) {
			CharTermAttribute charTermAttribute = tokens.getAttribute(CharTermAttribute.class);
			String string=charTermAttribute.toString();
			if (dic.contains(string))
				v.add(string);
		}

		return v;
	}

	public void setTrainPath(String folderPat){ trainPath=folderPat;/*"./file/dic/train"; */}
	public void setTestPath(String testPat){
		testPath=testPat;
	}
	public  void trainBySql() throws SQLException {
		String url="jdbc:mysql://localhost:3306/first?user=root&password=root&useUnicode=true&characterEncoding=UTF-8";

		long startTime=System.currentTimeMillis();  //开始时
		com.mysql.jdbc.Driver driver=new com.mysql.jdbc.Driver();
		Connection conn= DriverManager.getConnection(url);
		Statement statement=conn.createStatement();
		//File folder=new File(trainPath); //多文件夹
		int articleNum=0;//文件总数
		labelsName=new String[]{"pos","neg"};
		labels=new ArrayList<Label>();
		for(int i=0;i<labelsName.length;i++){
			labels.add(new Label());//每个一标签
			String sql="select COUNT(content) from "+labelsName[i]+"_train";
			ResultSet ret=statement.executeQuery(sql);
			ret.next();
			int size=ret.getInt(1);
			sql="select content from "+labelsName[i]+"_train";
			ret=statement.executeQuery(sql);
			String[] article=new String[size];
			for(int j=0;ret.next();j++)
				article[j]=ret.getString("content");

			articleNum+=article.length;
			System.out.println("Processing:"+labelsName[i]);
			GUI.setTextArea("Processing:"+labelsName[i]);
			ArrayList<String> v=new ArrayList<String>();
			for(int j=0;j<article.length;j++){
				try {
					v.addAll(readArticle(article[j]));
					if(v.size()==0)
						System.out.println(article[j]);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			//把当前类别标签下的所有文档的所有单词都放入Set集合中，
			//目的是为了获得vocabulary的大小
			vocabulary.addAll(v);
			//对当前类别标签下的所有文档的所有单词进行排序，
			//目的是为了方便接下来统计各个单词的信息
			String[] allWords=new String[v.size()];
			for(int j=0;j<v.size();j++)
				allWords[j]=v.get(j);
			sort(allWords,0,v.size()-1);
			//统计各个单词的信息
			String previous=allWords[0];
			double count=1;
			Map<String,WordItem> m=new HashMap<String, WordItem>();
			for(int j=1;j<allWords.length;j++){
				if(allWords[j].equals(previous))
					count++;
				else{
					m.put(previous, new WordItem(count));
					previous=allWords[j];
					count=1;
				}
			}
			labels.get(i).set(m, v.size(),article.length);
			long endTime=System.currentTimeMillis();
			trainTime=endTime-startTime;
		}
		//获得了vocabulary的大小后，下面才开始计算词频
		for(int i=0;i<labels.size();i++){
			labels.get(i).setRate(labels.get(i).documentCount/articleNum);
			Iterator iter=labels.get(i).m.entrySet().iterator();
			while(iter.hasNext()) {
				Map.Entry<String, WordItem> entry = (Map.Entry<String, WordItem>) iter.next();
				WordItem item = entry.getValue();
				item.setFrequency((item.count) / (labels.get(i).wordCount));
			}
		}



	}

	public void train() {
		long startTime=System.currentTimeMillis();  //开始时间
		File folder=new File(trainPath); //多文件夹
		int fileNumber=0;//文件总数
		labelsName=folder.list();//比如 军事 文化 学习 手机
		labels=new ArrayList<Label>();
		for(int i=0;i<labelsName.length;i++){
			labels.add(new Label());//每个一标签
			File subFolder=new File(trainPath+"/"+labelsName[i]);
			String[] files=subFolder.list();//每一个文件
			fileNumber+=files.length;
			System.out.println("Processing:"+labelsName[i]);
			GUI.setTextArea("Processing:"+labelsName[i]);
			ArrayList<String> v=new ArrayList<String>();
			for(int j=0;j<files.length;j++){
				try {
					v.addAll(readFile(trainPath+"/"+labelsName[i]+"/"+files[j]));
					if(v.size()==0)
						System.out.println(files[j]);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			//把当前类别标签下的所有文档的所有单词都放入Set集合中，
			//目的是为了获得vocabulary的大小
			vocabulary.addAll(v);
			//对当前类别标签下的所有文档的所有单词进行排序，
			//目的是为了方便接下来统计各个单词的信息
			String[] allWords=new String[v.size()];
			for(int j=0;j<v.size();j++)
				allWords[j]=v.get(j);
			sort(allWords,0,v.size()-1);
			//统计各个单词的信息
			String previous=allWords[0];
			double count=1;
			Map<String,WordItem> m=new HashMap<String, WordItem>();
			for(int j=1;j<allWords.length;j++){
				if(allWords[j].equals(previous))
					count++;
				else{
					m.put(previous, new WordItem(count));
					previous=allWords[j];
					count=1;
				}
			}
			labels.get(i).set(m, v.size(),files.length);
			long endTime=System.currentTimeMillis();
			trainTime=endTime-startTime;
		}
		//获得了vocabulary的大小后，下面才开始计算词频
		for(int i=0;i<labels.size();i++){
			labels.get(i).setRate(labels.get(i).documentCount/fileNumber);
			Iterator iter=labels.get(i).m.entrySet().iterator();
			while(iter.hasNext()) {
				Map.Entry<String, WordItem> entry = (Map.Entry<String, WordItem>) iter.next();
				WordItem item = entry.getValue();
				item.setFrequency((item.count) / (labels.get(i).wordCount));
			}
		}
	}

	public void test() {
		ArrayList<String> v = null;
		try {
			v = readFile(testPath);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		double []values=culSentiment(v);

		//for(int i=0;i<values.length;i++)
		//所有的概率均取以10为底的log值
		//System.out.println(labelsName[i]+"的概率为"+values[i]);
		int maxIndex = findMax(values);
		if (maxIndex >= 0) {
			//System.out.println(testPath + " belongs to " + labelsName[maxIndex]);
			label = labelsName[maxIndex];
		} else {
			//System.out.println(testPath + " belongs to other");
			label = "other";

		}
		//	GUI.setTextArea(testPath+" belongs to "+labelsName[maxIndex]);

		/*for(double l :values)
			System.out.println(l);*/
		File a=new File("1"),b=new File("0");
		try {
			FileOutputStream fs = new FileOutputStream(a,true);
			PrintStream t = new PrintStream(fs);
			FileOutputStream fb = new FileOutputStream(b,true);
			PrintStream f = new PrintStream(fb);
			//p.println(100);
			String[] temp = testPath.split("/");
			double rate=values[0] / values[1] > (values[1] / values[0]) ? values[0] / values[1] : (values[1] / values[0]);
			if(testPath.contains("pos.721.txt"))
				String.valueOf('a');
			if (!getLabelName().equals(temp[temp.length - 2])) {
			/*System.out.println(testPath + " belongs to "+label);
			for(double l :values)
				System.out.println(l);*/
				if (rate>1000)
					System.out.println(testPath+"    "+rate);
				//f.write((new Double(rate).toString()+"\n").getBytes());
			}//else
				//t.write((new Double(rate).toString()+"\n").getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}


	}

	private double[] culSentiment(ArrayList<String> v){
		double values[] = new double[labelsName.length];
		for (int i = 0; i < labels.size(); i++) { //遍历每个标签
			double tempValue = 1;//* 文档频率
			int notFind = 0;
			for (int j = 0; j < v.size(); j++) {//遍历每个单词
				if (labels.get(i).m.containsKey(v.get(j))) {//如果某个单词在某个lable中出现过
					tempValue *= labels.get(i).m.get(v.get(j)).frequency;//tempValue与其在lables中的词语频相加
				} else {//如果没出现 可能性加一个很小的值
					tempValue *= 1 / (double) (labels.get(i).wordCount);
					notFind++;
				}
			}

			values[i] = tempValue * labels.get(i).rate;
			if (notFind == v.size())
				values[i] = 0;
		}
		return values;
	}

	public String getLabelName(){
		return label;
	}
	public long getTrainingTime(){
		return trainTime;
	}
	class Label{//类别标签：体育、经济、政治等等
		//m中用来存放每个单词及其统计信息
		Map<String,WordItem> m=new HashMap<String,WordItem>();
		double wordCount;//某个类别标签下的所有单词个数
		double documentCount;//某个类别标签下的所有文档个数
		double rate;
		public Label() {
			this.m=null;
			this.wordCount=-1;
			this.documentCount=-1;
		}
		public void set(Map<String,WordItem> m,double wordCount,double documentCount) {
			this.m=m;
			this.wordCount=wordCount;
			this.documentCount=documentCount;
		}

		public void setRate(double rate) {
			this.rate = rate;
		}
	}
	class WordItem{//单词的统计信息包括单词的个数和词频
		double count;//单词的个数
		double frequency;//词频，它需要在得出vocabulary的大小之后才能计算
		public WordItem(double count) {
			this.count=count;
			this.frequency=-1;
		}
		public void setFrequency(double frequency){
			this.frequency=frequency;
		}
	}

}
