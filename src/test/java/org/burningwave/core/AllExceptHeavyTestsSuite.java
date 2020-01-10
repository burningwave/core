package org.burningwave.core;

import org.junit.platform.runner.JUnitPlatform;
import org.junit.platform.suite.api.ExcludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@SelectPackages("org.burningwave.core")
@ExcludeTags("Heavy")
public class AllExceptHeavyTestsSuite {

}
