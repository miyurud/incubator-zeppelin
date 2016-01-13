/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.wso2.ml.predict;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import org.apache.spark.HttpServer;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkEnv;
import org.apache.spark.repl.SparkCommandLine;
import org.apache.spark.repl.SparkILoop;
import org.apache.spark.repl.SparkIMain;
import org.apache.spark.repl.SparkJLineCompletion;
import org.apache.spark.scheduler.Pool;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.ui.jobs.JobProgressListener;
import org.apache.zeppelin.interpreter.*;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.apache.zeppelin.spark.DepInterpreter;
import org.apache.zeppelin.spark.SparkVersion;
import org.apache.zeppelin.spark.ZeppelinContext;
import org.apache.zeppelin.spark.dep.DependencyContext;
import org.apache.zeppelin.spark.dep.DependencyResolver;
import org.apache.zeppelin.wso2.ml.JerseyClient;
import org.apache.zeppelin.wso2.ml.WSO2MLInterface;
import org.apache.zeppelin.wso2.ml.project.WSO2MLInterpreterProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.ml.commons.domain.MLDataset;
import org.wso2.carbon.ml.commons.domain.config.MLAlgorithm;
import scala.Enumeration;
import scala.None;
import scala.Some;
import scala.tools.nsc.Settings;
import scala.tools.nsc.settings.MutableSettings;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Shell interpreter for Zeppelin.
 *
 * @author Leemoonsoo
 * @author anthonycorbacho
 *
 */
public class WSO2MLInterpreterPrediction extends Interpreter {
  private Logger logger = LoggerFactory.getLogger(WSO2MLInterpreterPrediction.class);
  private int commandTimeOut = 600000;
  private String ML_SERVER_HOST = "localhost";
  private InterpreterContext ctx;
  private SparkContext sc;
  private SQLContext sqlc;
  private ZeppelinContext z;
  private SparkEnv env;
  private DependencyResolver dep;
  private JobProgressListener sparkListener;
  private SparkILoop interpreter;
  private SparkVersion sparkVersion;
  private ByteArrayOutputStream out;
  private SparkIMain intp;
  private SparkJLineCompletion completor;
  private Map<String, Object> binder;

  static {
    Interpreter.register("predict", "wso2ml", WSO2MLInterpreterPrediction.class.getName());
  }

  public WSO2MLInterpreterPrediction(Properties property) {
    super(property);
    out = new ByteArrayOutputStream();
  }

  @Override
  public void close() {}

  public void getMainSparkContext(){
    InterpreterGroup intpGroup = getInterpreterGroup();
    synchronized (intpGroup) {
      for (Interpreter intp : getInterpreterGroup()){
        LazyOpenInterpreter lazy = null;
        if (intp.getClassName().equals(WSO2MLInterpreterProject.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            if (p instanceof LazyOpenInterpreter) {
              lazy = (LazyOpenInterpreter) p;
            }
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          sc = ((WSO2MLInterpreterProject) p).getSparkContext();
        }

      }
    }

    System.err.println("+=+>" + sc.version());
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext contextInterpreter) {
    this.ctx = contextInterpreter;
    StringBuilder template = new StringBuilder("");

    String debug = "";

    try {
    ClassLoader classLoader = getClass().getClassLoader();
    URL dataFile = classLoader.getResource("mlwidget_predict.ftl");
    InputStreamReader dataReader = new InputStreamReader(dataFile.openStream());
      BufferedReader buff = new BufferedReader(dataReader);
    String line = null;

      while ((line = buff.readLine()) != null) {
        template.append(line).append("\n");
      }
      dataReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    Map root = new HashMap();
    StringWriter out = new StringWriter();

    boolean flag = true;
    List<String> list = WSO2MLInterface.getListOfModels();

    if(list == null) {
      flag = false;
    }

    if(list.size() == 0){
      root.put("notEmpty", false);
    }else{
      root.put("notEmpty", true);
      root.put("models", list);
    }

    if(flag) {
      Configuration cfg = new Configuration(Configuration.VERSION_2_3_22);
      cfg.setClassForTemplateLoading(this.getClass(), "/");
      cfg.setDefaultEncoding("UTF-8");
      cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

      try {
        Template temp = cfg.getTemplate("mlwidget_predict.ftl");
        temp.process(root, out);
      } catch (IOException e) {
        e.printStackTrace();
      } catch (TemplateException e) {
        e.printStackTrace();
      }
    }

    if(flag){
      List<InterpreterContextRunner> lst2 = this.ctx.getRunners();

      Iterator<InterpreterContextRunner> itr = lst2.iterator();
      StringBuilder sb = new StringBuilder();
      while(itr.hasNext()){
        InterpreterContextRunner obj = itr.next();

        sb.append(obj.getNoteId() + "|");
      }

      sb.append(this.ctx.getAngularObjectRegistry().getAll(this.ctx.getNoteId()).size());
      Iterator<Job> jobs = (Iterator<Job>) this.getScheduler().getJobsWaiting().iterator();

      while(jobs.hasNext()) {
        sb.append(jobs.next().getJobName() + "|");
      }

      jobs = (Iterator<Job>) this.getScheduler().getJobsRunning().iterator();

      while(jobs.hasNext()) {
        sb.append(jobs.next().getJobName() + "|");
      }

      //println(scala.tools.nsc.Properties.versionString)

      InterpreterResult res = interpretInput(new String[]{"println(\"Hello\")"});

      return new InterpreterResult(InterpreterResult.Code.SUCCESS,
              "%html " + out.toString());
    }else{
      return new InterpreterResult(InterpreterResult.Code.ERROR,
              "%html <h4>Error in connecting to the WSO2 ML REST API. Please check whether the REST API is up and is accessible.</h4>");
    }
  }

  public InterpreterResult interpretInput(String[] lines) {
    SparkEnv.set(env);
    // add print("") to make sure not finishing with comment
    // see https://github.com/NFLabs/zeppelin/issues/151
    String[] linesToRun = new String[lines.length + 1];
    for (int i = 0; i < lines.length; i++) {
      linesToRun[i] = lines[i];
    }
    linesToRun[lines.length] = "print(\"\")";
    scala.Console.setOut((java.io.PrintStream) binder.get("out"));
    out.reset();
    InterpreterResult.Code r = null;
    String incomplete = "";

    for (int l = 0; l < linesToRun.length; l++) {
      String s = linesToRun[l];
      // check if next line starts with "." (but not ".." or "./") it is treated as an invocation
      if (l + 1 < linesToRun.length) {
        String nextLine = linesToRun[l + 1].trim();
        if (nextLine.startsWith(".") && !nextLine.startsWith("..") && !nextLine.startsWith("./")) {
          incomplete += s + "\n";
          continue;
        }
      }

      scala.tools.nsc.interpreter.Results.Result res = null;
      try {
        res = intp.interpret(incomplete + s);
      } catch (Exception e) {
        sc.clearJobGroup();
        logger.info("Interpreter exception", e);
        return new InterpreterResult(InterpreterResult.Code.ERROR, InterpreterUtils.getMostRelevantMessage(e));
      }

      r = getResultCode(res);

      if (r == InterpreterResult.Code.ERROR) {
        sc.clearJobGroup();
        return new InterpreterResult(r, out.toString());
      } else if (r == InterpreterResult.Code.INCOMPLETE) {
        incomplete += s + "\n";
      } else {
        incomplete = "";
      }
    }

    if (r == InterpreterResult.Code.INCOMPLETE) {
      return new InterpreterResult(r, "Incomplete expression");
    } else {
      return new InterpreterResult(r, out.toString());
    }
  }

  private InterpreterResult.Code getResultCode(scala.tools.nsc.interpreter.Results.Result r) {
    if (r instanceof scala.tools.nsc.interpreter.Results.Success$) {
      return InterpreterResult.Code.SUCCESS;
    } else if (r instanceof scala.tools.nsc.interpreter.Results.Incomplete$) {
      return InterpreterResult.Code.INCOMPLETE;
    } else {
      return InterpreterResult.Code.ERROR;
    }
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetFIFOScheduler(
            WSO2MLInterpreterPrediction.class.getName() + this.hashCode());
  }

  public Object getValue(String name) {
    Object ret = intp.valueOfTerm(name);
    if (ret instanceof None) {
      return null;
    } else if (ret instanceof Some) {
      return ((Some) ret).get();
    } else {
      return ret;
    }
  }

  @Override
  public List<String> completion(String buf, int cursor) {
    return null;
  }



  private List<String> getListOfDataSets(){
    //First, we retrieve all the names of the data sets that are already loaded into the ML server
    ArrayList<String> list = null;

    try {
      JerseyClient client = new JerseyClient();
      client.setUsernamePassword("admin", "admin");
      Object response = client.get_JSON("https://" + ML_SERVER_HOST + ":9443/api/datasets", String.class);

      String sbAlgos = response.toString();
      list = new ArrayList<String>();

      MLDataset[] arr = new Gson().fromJson(sbAlgos, MLDataset[].class);

      for (MLDataset dataset : arr) {
        list.add(dataset.getName());
      }
    }catch(Exception e){
      logger.error("Error: " + e.getMessage());
    }

    //Second, we add all the tables that are currently loaded to the Spark SQL context
    System.err.println("------------ Miyuru 42 -------------|");
    for (String item : sqlc.tableNames()){
      System.err.println("------------ Miyuru 3 -------------|" + item);
      list.add(item);
    }

    return list;
  }

  public synchronized SparkContext getSparkContext() {
    if (sc == null) {
      sc = createSparkContext();
      env = SparkEnv.get();
      sparkListener = setupListeners(sc);
    }
    return sc;
  }

  static JobProgressListener setupListeners(SparkContext context) {
    JobProgressListener pl = new JobProgressListener(context.getConf());
    try {
      Object listenerBus = context.getClass().getMethod("listenerBus").invoke(context);

      Method[] methods = listenerBus.getClass().getMethods();
      Method addListenerMethod = null;
      for (Method m : methods) {
        if (!m.getName().equals("addListener")) {
          continue;
        }

        Class<?>[] parameterTypes = m.getParameterTypes();

        if (parameterTypes.length != 1) {
          continue;
        }

        if (!parameterTypes[0].isAssignableFrom(JobProgressListener.class)) {
          continue;
        }

        addListenerMethod = m;
        break;
      }

      if (addListenerMethod != null) {
        addListenerMethod.invoke(listenerBus, pl);
      } else {
        return null;
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
      e.printStackTrace();
      return null;
    }
    return pl;
  }

  @Override
  public void open() {
    System.err.println("========+++++ Open called ++-");
    getMainSparkContext();
    URL[] urls = getClassloaderUrls();

    // Very nice discussion about how scala compiler handle classpath
    // https://groups.google.com/forum/#!topic/scala-user/MlVwo2xCCI0

    /*
     * > val env = new nsc.Settings(errLogger) > env.usejavacp.value = true > val p = new
     * Interpreter(env) > p.setContextClassLoader > Alternatively you can set the class path through
     * nsc.Settings.classpath.
     *
     * >> val settings = new Settings() >> settings.usejavacp.value = true >>
     * settings.classpath.value += File.pathSeparator + >> System.getProperty("java.class.path") >>
     * val in = new Interpreter(settings) { >> override protected def parentClassLoader =
     * getClass.getClassLoader >> } >> in.setContextClassLoader()
     */
    Settings settings = new Settings();
    if (getProperty("args") != null) {
      String[] argsArray = getProperty("args").split(" ");
      LinkedList<String> argList = new LinkedList<String>();
      for (String arg : argsArray) {
        argList.add(arg);
      }

      SparkCommandLine command =
              new SparkCommandLine(scala.collection.JavaConversions.asScalaBuffer(
                      argList).toList());
      settings = command.settings();
    }

    // set classpath for scala compiler
    MutableSettings.PathSetting pathSettings = settings.classpath();
    String classpath = "";
    List<File> paths = currentClassPath();
    for (File f : paths) {
      if (classpath.length() > 0) {
        classpath += File.pathSeparator;
      }
      classpath += f.getAbsolutePath();
    }

    if (urls != null) {
      for (URL u : urls) {
        if (classpath.length() > 0) {
          classpath += File.pathSeparator;
        }
        classpath += u.getFile();
      }
    }

    // add dependency from DepInterpreter
    DepInterpreter depInterpreter = getDepInterpreter();
    if (depInterpreter != null) {
      DependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFiles();
        if (files != null) {
          for (File f : files) {
            if (classpath.length() > 0) {
              classpath += File.pathSeparator;
            }
            classpath += f.getAbsolutePath();
          }
        }
      }
    }

    pathSettings.v_$eq(classpath);
    settings.scala$tools$nsc$settings$ScalaSettings$_setter_$classpath_$eq(pathSettings);


    // set classloader for scala compiler
    settings.explicitParentLoader_$eq(new Some<ClassLoader>(Thread.currentThread()
            .getContextClassLoader()));
    MutableSettings.BooleanSetting b = (MutableSettings.BooleanSetting) settings.usejavacp();
    b.v_$eq(true);
    settings.scala$tools$nsc$settings$StandardScalaSettings$_setter_$usejavacp_$eq(b);

    /* spark interpreter */
    this.interpreter = new SparkILoop(null, new PrintWriter(out));
    interpreter.settings_$eq(settings);

    interpreter.createInterpreter();

    intp = interpreter.intp();
    intp.setContextClassLoader();
    intp.initializeSynchronous();

    completor = new SparkJLineCompletion(intp);

    sc = getSparkContext();
    if (sc.getPoolForName("fair").isEmpty()) {
      Enumeration.Value schedulingMode = org.apache.spark.scheduler.SchedulingMode.FAIR();
      int minimumShare = 0;
      int weight = 1;
      Pool pool = new Pool("fair", schedulingMode, minimumShare, weight);
      sc.taskScheduler().rootPool().addSchedulable(pool);
    }

    sparkVersion = SparkVersion.fromVersionString(sc.version());

    sqlc = getSQLContext();

    dep = getDependencyResolver();

    PrintStream printStream = new PrintStream(out);
    z = new ZeppelinContext(sc, sqlc, null, dep, printStream,
            Integer.parseInt(getProperty("zeppelin.spark.maxResult")));

    intp.interpret("@transient var _binder = new java.util.HashMap[String, Object]()");
    binder = (Map<String, Object>) getValue("_binder");
    binder.put("sc", sc);
    binder.put("sqlc", sqlc);
    binder.put("z", z);
    binder.put("out", printStream);

    intp.interpret("@transient val z = "
            + "_binder.get(\"z\").asInstanceOf[org.apache.zeppelin.spark.ZeppelinContext]");
    intp.interpret("@transient val sc = "
            + "_binder.get(\"sc\").asInstanceOf[org.apache.spark.SparkContext]");
    intp.interpret("@transient val sqlc = "
            + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
    intp.interpret("@transient val sqlContext = "
            + "_binder.get(\"sqlc\").asInstanceOf[org.apache.spark.sql.SQLContext]");
    intp.interpret("import org.apache.spark.SparkContext._");

    if (sparkVersion.oldSqlContextImplicits()) {
      intp.interpret("import sqlContext._");
    } else {
      intp.interpret("import sqlContext.implicits._");
      intp.interpret("import sqlContext.sql");
      intp.interpret("import org.apache.spark.sql.functions._");
    }
    /* Temporary disabling DisplayUtils. see https://issues.apache.org/jira/browse/ZEPPELIN-127
     *
    // Utility functions for display
    intp.interpret("import org.apache.zeppelin.spark.utils.DisplayUtils._");

    // Scala implicit value for spark.maxResult
    intp.interpret("import org.apache.zeppelin.spark.utils.SparkMaxResult");
    intp.interpret("implicit val sparkMaxResult = new SparkMaxResult(" +
            Integer.parseInt(getProperty("zeppelin.spark.maxResult")) + ")");
     */

    try {
      if (sparkVersion.oldLoadFilesMethodName()) {
        Method loadFiles = this.interpreter.getClass().getMethod("loadFiles", Settings.class);
        loadFiles.invoke(this.interpreter, settings);
      } else {
        Method loadFiles = this.interpreter.getClass().getMethod(
                "org$apache$spark$repl$SparkILoop$$loadFiles", Settings.class);
        loadFiles.invoke(this.interpreter, settings);
      }
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
      throw new InterpreterException(e);
    }

    // add jar
    if (depInterpreter != null) {
      DependencyContext depc = depInterpreter.getDependencyContext();
      if (depc != null) {
        List<File> files = depc.getFilesDist();
        if (files != null) {
          for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".jar")) {
              sc.addJar(f.getAbsolutePath());
              logger.info("sc.addJar(" + f.getAbsolutePath() + ")");
            } else {
              sc.addFile(f.getAbsolutePath());
              logger.info("sc.addFile(" + f.getAbsolutePath() + ")");
            }
          }
        }
      }
    }
  }

  private DepInterpreter getDepInterpreter() {
    InterpreterGroup intpGroup = getInterpreterGroup();
    if (intpGroup == null) return null;
    synchronized (intpGroup) {
      for (Interpreter intp : intpGroup) {
        if (intp.getClassName().equals(DepInterpreter.class.getName())) {
          Interpreter p = intp;
          while (p instanceof WrappedInterpreter) {
            p = ((WrappedInterpreter) p).getInnerInterpreter();
          }
          return (DepInterpreter) p;
        }
      }
    }
    return null;
  }

  private List<File> currentClassPath() {
    List<File> paths = classPath(Thread.currentThread().getContextClassLoader());
    String[] cps = System.getProperty("java.class.path").split(File.pathSeparator);
    if (cps != null) {
      for (String cp : cps) {
        paths.add(new File(cp));
      }
    }
    return paths;
  }

  private List<File> classPath(ClassLoader cl) {
    List<File> paths = new LinkedList<File>();
    if (cl == null) {
      return paths;
    }

    if (cl instanceof URLClassLoader) {
      URLClassLoader ucl = (URLClassLoader) cl;
      URL[] urls = ucl.getURLs();
      if (urls != null) {
        for (URL url : urls) {
          paths.add(new File(url.getFile()));
        }
      }
    }
    return paths;
  }

  public SQLContext getSQLContext() {
    if (sqlc == null) {
      if (useHiveContext()) {
        String name = "org.apache.spark.sql.hive.HiveContext";
        Constructor<?> hc;
        try {
          hc = getClass().getClassLoader().loadClass(name)
                  .getConstructor(SparkContext.class);
          sqlc = (SQLContext) hc.newInstance(getSparkContext());
        } catch (NoSuchMethodException | SecurityException
                | ClassNotFoundException | InstantiationException
                | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
          logger.warn("Can't create HiveContext. Fallback to SQLContext", e);
          // when hive dependency is not loaded, it'll fail.
          // in this case SQLContext can be used.
          sqlc = new SQLContext(getSparkContext());
        }
      } else {
        sqlc = new SQLContext(getSparkContext());
      }
    }

    return sqlc;
  }

  public DependencyResolver getDependencyResolver() {
    if (dep == null) {
      dep = new DependencyResolver(intp,
              sc,
              getProperty("zeppelin.dep.localrepo"),
              getProperty("zeppelin.dep.additionalRemoteRepository"));
    }
    return dep;
  }

  private boolean useHiveContext() {
    return Boolean.parseBoolean(getProperty("zeppelin.spark.useHiveContext"));
  }

  public SparkContext createSparkContext() {
    System.err.println("------ Create new SparkContext " + getProperty("master") + " -------");

    String execUri = System.getenv("SPARK_EXECUTOR_URI");
    String[] jars = SparkILoop.getAddedJars();

    String classServerUri = null;

    try { // in case of spark 1.1x, spark 1.2x
      Method classServer = interpreter.intp().getClass().getMethod("classServer");
      HttpServer httpServer = (HttpServer) classServer.invoke(interpreter.intp());
      classServerUri = httpServer.uri();
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException
            | IllegalArgumentException | InvocationTargetException e) {
      // continue
    }

    if (classServerUri == null) {
      try { // for spark 1.3x
        Method classServer = interpreter.intp().getClass().getMethod("classServerUri");
        classServerUri = (String) classServer.invoke(interpreter.intp());
      } catch (NoSuchMethodException | SecurityException | IllegalAccessException
              | IllegalArgumentException | InvocationTargetException e) {
        throw new InterpreterException(e);
      }
    }

    SparkConf conf =
            new SparkConf()
                    .setMaster(getProperty("master"))
                    .setAppName(getProperty("spark.app.name"))
                    .set("spark.repl.class.uri", classServerUri)
                    .set("spark.ui.port", "4042");

    if (jars.length > 0) {
      conf.setJars(jars);
    }

    if (execUri != null) {
      conf.set("spark.executor.uri", execUri);
    }
    if (System.getenv("SPARK_HOME") != null) {
      conf.setSparkHome(System.getenv("SPARK_HOME"));
    }
    conf.set("spark.scheduler.mode", "FAIR");

    Properties intpProperty = getProperty();

    for (Object k : intpProperty.keySet()) {
      String key = (String) k;
      String val = toString(intpProperty.get(key));
      if (!key.startsWith("spark.") || !val.trim().isEmpty()) {
        logger.debug(String.format("SparkConf: key = [%s], value = [%s]", key, val));
        conf.set(key, val);
      }
    }

    //TODO(jongyoul): Move these codes into PySparkInterpreter.java
    String pysparkBasePath = getSystemDefault("SPARK_HOME", "spark.home", null);
    File pysparkPath;
    if (null == pysparkBasePath) {
      pysparkBasePath = getSystemDefault("ZEPPELIN_HOME", "zeppelin.home", "../");
      pysparkPath = new File(pysparkBasePath,
              "interpreter" + File.separator + "spark" + File.separator + "pyspark");
    } else {
      pysparkPath = new File(pysparkBasePath,
              "python" + File.separator + "lib");
    }

    String[] pythonLibs = new String[]{"pyspark.zip", "py4j-0.8.2.1-src.zip"};
    ArrayList<String> pythonLibUris = new ArrayList<>();
    for (String lib : pythonLibs) {
      File libFile = new File(pysparkPath, lib);
      if (libFile.exists()) {
        pythonLibUris.add(libFile.toURI().toString());
      }
    }
    pythonLibUris.trimToSize();
    if (pythonLibs.length == pythonLibUris.size()) {
      conf.set("spark.yarn.dist.files", Joiner.on(",").join(pythonLibUris));
      if (!useSparkSubmit()) {
        conf.set("spark.files", conf.get("spark.yarn.dist.files"));
      }
      conf.set("spark.submit.pyArchives", Joiner.on(":").join(pythonLibs));
    }


    SparkContext sparkContext = new SparkContext(conf);
    //SparkContext sparkContext = new SparkContext("spark://10.100.7.23:7077", "wso2ml", conf);
    return sparkContext;
  }

  static final String toString(Object o) {
    return (o instanceof String) ? (String) o : "";
  }

  private boolean useSparkSubmit() {
    return null != System.getenv("SPARK_SUBMIT");
  }

  public static String getSystemDefault(
          String envName,
          String propertyName,
          String defaultValue) {

    if (envName != null && !envName.isEmpty()) {
      String envValue = System.getenv().get(envName);
      if (envValue != null) {
        return envValue;
      }
    }

    if (propertyName != null && !propertyName.isEmpty()) {
      String propValue = System.getProperty(propertyName);
      if (propValue != null) {
        return propValue;
      }
    }
    return defaultValue;
  }
}
