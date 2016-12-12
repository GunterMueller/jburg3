#include <stdio.h>
#include <stdlib.h>

#include "Test.h"
#include "Testcase.h"
#include "CppTestReducer.h"

int failureCount = 0;

void checkResult(int expected, int actual, const std::string& testname)
{
	if (expected == actual) {
		printf("Succeeded: %s\n", testname.c_str());
	} else {
		printf("FAILED: %s, expected %d != actual %d\n", testname.c_str(), expected, actual);
		failureCount++;
	}
}

void checkResult(std::string expected, std::string actual, const std::string& testname)
{
	if (expected == actual) {
		printf("Succeeded: %s\n", testname.c_str());
	} else {
		printf("FAILED: %s, expected %s != actual %s\n", testname.c_str(), expected.c_str(), actual.c_str());
		failureCount++;
	}
}

void runTest(Testcase& testcase)
{
    try {
        CppTestReducer  reducer;
        Calculator      calculator;
        reducer.label(calculator, testcase.root);
        Object result = reducer.reduce(calculator, testcase.root, testcase.valueType);

        if (testcase.valueType == Nonterminal::String) {
            checkResult(testcase.expectedValue, result.stringValue, testcase.name);
        } else {
            checkResult(atoi(testcase.expectedValue.c_str()), result.intValue, testcase.name);
        }

    } catch (std::logic_error& exception) {
        // TODO: Negative testcase logic
        printf("FAILED: %s, exception %s\n", testcase.name.c_str(), exception.what());
        printf("%s\n", toXML(testcase.root).c_str());
        failureCount++;
    }
}

int main(int argc, char* argv[])
{
    for (Testcase& testcase: buildTestcases(argv[1])) {
        runTest(testcase);
    }

    return failureCount;
}
