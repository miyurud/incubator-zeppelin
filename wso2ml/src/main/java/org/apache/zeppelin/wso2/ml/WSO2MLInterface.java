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

package org.apache.zeppelin.wso2.ml;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.ml.commons.domain.MLDataset;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.commons.domain.MLProject;
import org.wso2.carbon.ml.commons.domain.config.MLAlgorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by miyurud on 11/9/15.
 */
public class WSO2MLInterface {
    private static String ML_SERVER_HOST = "localhost";
    private static Logger logger = LoggerFactory.getLogger(WSO2MLInterface.class);

    public static List<String> getListOfDataSets(){
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

//        //Second, we add all the tables that are currently loaded to the Spark SQL context
//        System.err.println("------------ Miyuru 42 -------------|");
//        for (String item : sqlc.tableNames()){
//            System.err.println("------------ Miyuru 3 -------------|" + item);
//            list.add(item);
//        }

        return list;
    }

    public static List<String> getListOfAlgorithms(){

        ArrayList<String> list = null;

        try {
            JerseyClient client = new JerseyClient();
            client.setUsernamePassword("admin", "admin");

            Object response = client.get_JSON("https://" + ML_SERVER_HOST + ":9443/api/configs/algorithms", String.class);
            String sbAlgos = response.toString();
            list = new ArrayList<String>();

            MLAlgorithm[] arr = new Gson().fromJson(sbAlgos, MLAlgorithm[].class);

            for (MLAlgorithm algo : arr) {
                list.add(algo.getName());
            }
        }catch(Exception e){
            logger.error("Error: " + e.getMessage());
        }
        return list;
    }

    public static List<String> getListOfProjects(){

        ArrayList<String> list = null;

        try {
            JerseyClient client = new JerseyClient();
            client.setUsernamePassword("admin", "admin");

            Object response = client.get_JSON("https://" + ML_SERVER_HOST + ":9443/api/projects", String.class);
            String sbProjects = response.toString();
            list = new ArrayList<String>();

            MLProject[] arr = new Gson().fromJson(sbProjects, MLProject[].class);

            for (MLProject proj : arr) {
                list.add(proj.getName());
            }
        }catch(Exception e){
            logger.error("Error: " + e.getMessage());
        }
        return list;
    }

    public static List<String> getListOfModels(){

        ArrayList<String> list = null;

        try {
            JerseyClient client = new JerseyClient();
            client.setUsernamePassword("admin", "admin");

            Object response = client.get_JSON("https://" + ML_SERVER_HOST + ":9443/api/models", String.class);
            String sbModels = response.toString();
            list = new ArrayList<String>();

            MLModel[] arr = new Gson().fromJson(sbModels, MLModel[].class);
            String algoName = null;
            for (MLModel model : arr) {
                algoName = model.getAlgorithmName();
                if(algoName != null) {
                    list.add(algoName);
                }
            }
        }catch(Exception e){
            logger.error("Error: " + e.getMessage());
        }
        return list;
    }
}
