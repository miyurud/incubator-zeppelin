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
        function myFunction() {
            //document.getElementById("demo").innerHTML = "Hello World";

        }
    </script>
    <table>
        <tbody>
        <tr><td><h4>Prediction</h4></td></tr>
        <tr><td>Models : </td>
            <#if notEmpty>
                <td>
                    <select>
                        <#list models as ds>
                            <option value="${ds}">${ds}</option>
                        </#list>
                    </select>
                </td>
            <#else>
                <td>
No models found.
                </td>
            </#if>


        </tr>
        <tr><td>&nbsp;</td>
            <td>
                <button onclick="myFunction()">Run</button>
            </td>
        </tr>
        <tr><td>&nbsp;</td>
            <td>
                <p id="demo"></p>
            </td>
        </tr>
        </tbody>
    </table>
</div>