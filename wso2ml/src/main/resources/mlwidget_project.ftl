<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~    http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<div>
    <script>
        var ML_SERVER_HOST = "localhost";
        var serverUrl = "https://" + ML_SERVER_HOST + ":9443";

//        $( document ).ready(function() {
//            authenticate();
//        }

//        window.onload = function() {
//            alert("1")
//            authenticate();
//            alert("3")
//        };

        function authenticate(){
            var user = 'admin';
            var passwd = 'admin';

            if(validate(user) && validate(passwd)){
                var auth = user+":"+passwd;
                var authEncoded = btoa(auth)

                $.ajax({
                    url : 'http://10.100.7.23:8080/app/ajax/authenticate.jag',
                    data : {userName: user, password: passwd, authEncoded: authEncoded},
                    beforeSend : function(xhr) {
                        xhr.setRequestHeader("Authorization", "Basic YWRtaW46YWRtaW4=");
                    },
                    success : function(res){
                        console.log("before");
                        //getSessionCookie();
                        console.log("after");
                    },
                    error : function(res){
                        console.log(res);
//                        $('.wr-validation-summary').html('<p>'+res.responseJSON.status+'</p>');
//                        $('.wr-validation-summary').show();
                    }
                });
            } else {
//                $('.wr-validation-summary').show();
            }
        }

        function validate(text){
            if(!text || text.length == 0){
                return false;
            }
            return true;
        }

        function getSessionCookie() {
            var user = 'admin';
            var passwd = 'admin';
            var baseUrl = serverUrl;
            var auth = user+":"+passwd;
            var authEncoded = btoa(auth)
            // start a new login seesion and retrieve the cookie. Login must be authenticated with basic auth.
            $.ajax({
                type: "POST",
                url : baseUrl + '/api/login',
                contentType: "application/json",
                dataType: 'text',
                beforeSend : function(xhr) {
                    xhr.setRequestHeader("Authorization", "Basic " + authEncoded);
                },
                success : function(res){
//                    window.location.href = "../home/home.jag"
                    alert('successfully loggedin.')
                },
                error : function(res){
                    console.log(res);
//                    $('.wr-validation-summary').html('<p>'+res.responseJSON.status+'</p>');
//                    $('.wr-validation-summary').show();
                }
            });
        }

        function addProject() {
            //authenticate();
/*
            var user = 'admin';
            var passwd = 'admin';
            var auth = user+":"+passwd;
            var authEncoded = btoa(auth)

            var projectName = $('#textNewProjName').val();
            var projectDes = $('#textNewProjDescription').val();
            var datasetName = $( "#listDataSets option:selected" ).text();

            var projectNameValidation = isValidProjectName(projectName);
            var descriptionValidation = isValidDescription(projectDes);

            if(projectNameValidation && descriptionValidation){

                var jsonData = '{"name":"' + projectName + '","description":"' + projectDes + '","datasetName":"' + datasetName + '"}';

                $.ajax({
                    type: 'POST',
                    url: serverUrl+"/api/projects",
                    contentType: "application/json",
                    data : jsonData,
                    beforeSend : function(xhr) {
                        xhr.setRequestHeader("Authorization", "Basic " + authEncoded);
                    },
                    success : function(res){
                        //window.location.href = './projects.jag';
                        cancelAddProject();
                        document.getElementById('listProjects').add(projectName)
                        document.getElementById('listProjects').selectedIndex =1;
                    },
                    error : function(res){
                        var errorText = res.responseText;
                        alert(errorText);
                    }
                });
            }
            */
        }

        // Project name input validation
        function isValidProjectName(text){
            if(!text || text.length == 0){
                $('#project-name-error').text("* Project name is required.");
                return false;
            }
            if(/^[a-zA-Z0-9---_]*$/.test(text) == false){
                $('#project-name-error').text('* Special characters are not allowed');
                $('#project-name').text('');
                return false;
            }
            if(text.length > 100) {
                $('#project-name-error').text('* Project name is too long. Max 100 characters allowed.');
                return false;
            }
            return true;
        }

        function isValidDescription(text){
            if(text && text.length > 0){
                return true;
            }
            $('#project-description-error').text('* Project description is required.');
            return false;
        }

        function toggle(item, paneID){
            if(item =="New"){
                document.getElementById(paneID).style.visibility='visible'
            }else{
                document.getElementById(paneID).style.visibility='hidden'
            }
        }

        function cancelAddProject(){
            toggle('Old', 'id1');
            document.getElementById('textNewProjName').value="";
            document.getElementById('textNewProjDescription').value="";
            document.getElementById('listProjects').selectedIndex =1;
        }

        function cancelAddDataSet(){
            toggle('Old', 'id2');
            document.getElementById('textNewDataSetName').value="";
            document.getElementById('textFilePath').value="";
            document.getElementById('listDataSets').selectedIndex =1;
        }

    </script>
    <table>
        <tbody>
        <tr><td><h4>Project</h4></td></tr>
        <tr><td>Projects : </td>
            <td>
                <select id="listProjects" onchange="toggle(this.value, 'id1')">
                <#list projects as ds>
                    <option value="${ds}">${ds}</option>
                </#list>
                </select>
            </td>
            <td>
                <table id="id1" style="visibility:hidden">
                    <tr><td>New Project Name :</td>
                        <td>
                            <input id="textNewProjName"></input>
                        </td>
                    </tr>
                    <tr><td>Description :</td>
                        <td>
                            <input id="textNewProjDescription"></input>
                        </td>
                    </tr>
                    <tr>
                        <td>Data sets : </td>
                        <td>
                            <select id="listDataSets" onchange="toggle(this.value, 'id2')">
                            <#list datasets as ds>
                                <option value="${ds}">${ds}</option>
                            </#list>
                            </select>
                        </td>
                    </tr>
                    <tr><td>&nbsp;</td>
                    <tr>
                        <td>
                            <button onclick="addProject()">Add</button>
                        </td>
                        <td>&nbsp;</td>
                        <td>
                            <button id="btnCancelNewProject" onclick="cancelAddProject()">Cancel</button>
                        </td>
                    </tr>
                    </tr>
                </table>
            </td>
            <td>
                <table id="id2" style="visibility:hidden">
                    <tr><td>New Data Set Name :</td>
                        <td>
                            <input id="textNewDataSetName"></input>
                        </td>
                    </tr>
                    <tr><td>File path :</td>
                        <td>
                            <input type="file" id="textFilePath"></input>
                        </td>
                    </tr>
                    <tr><td>&nbsp;</td>
                    <tr>
                        <td>
                            <button onclick="addDataSet()">Add</button>
                        </td>
                        <td>&nbsp;</td>
                        <td>
                            <button id="btnCancelNewDataSet" onclick="cancelAddDataSet()">Cancel</button>
                        </td>
                    </tr>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td>
                <p id="notification-area"></p>
            </td>
        </tr>
        </tbody>
    </table>
</div>