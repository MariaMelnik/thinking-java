/*
 * Created on 13-3-4
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright @2013 the original author or authors.
 */
package chapter23_testng;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * TestNG Parameterized Test - Advance.
 *
 * @author XiongNeng
 * @version 1.0
 * @since 13-3-4
 */
public class TestNGTest6_3 {
    @Test(dataProvider = "Data-Provider-Function")
    public void parameterIntTest(UserModel param) {
        System.out.println("Parameterized Number is : " + param.getMsg());
        System.out.println("Parameterized Number is : " + param.getNumber());
    }

    //This function will provide the patameter data
    @DataProvider(name = "Data-Provider-Function")
    public Object[][] parameterIntTestProvider() {

        UserModel obj = new UserModel();
        obj.setMsg("Hello");
        obj.setNumber(123);

        return new Object[][]{
                {obj}
        };
    }
}