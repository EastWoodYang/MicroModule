package com.eastwood.tools.plugins.core

import com.android.build.api.dsl.model.ProductFlavor
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.dsl.BuildType
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project

class ProductFlavorInfo {

    List<String> flavorDimensions
    NamedDomainObjectContainer<ProductFlavor> productFlavors
    List<String> combinedProductFlavors
    Map<String, List<String>> combinedProductFlavorsMap
    boolean singleDimension
    NamedDomainObjectContainer<BuildType> buildTypes

    ProductFlavorInfo(Project project) {
        List<List<String>> splitProductFlavors = new ArrayList<>()
        BaseExtension extension = (BaseExtension) project.extensions.getByName("android")
        buildTypes = extension.buildTypes
        productFlavors = extension.productFlavors
        flavorDimensions = extension.flavorDimensionList
        if (flavorDimensions == null) {
            flavorDimensions = new ArrayList<>()
        }
        def flavorDimensionSize = flavorDimensions.size()
        for (int i = 0; i < flavorDimensionSize; i++) {
            splitProductFlavors.add(new ArrayList<>())
        }
        productFlavors.each {
            def position = flavorDimensions.indexOf(it.dimension)
            splitProductFlavors.get(position).add(it.name)
        }


        List<List<String>> splitProductFlavorsTemp = new ArrayList<>()
        splitProductFlavors.each {
            if (it.size() != 0) {
                splitProductFlavorsTemp.add(it)
            }
        }

        calculateCombination(splitProductFlavorsTemp)
        if (combinedProductFlavors.size() == productFlavors.size()) {
            singleDimension = true
        }
    }

    def calculateCombination(List<List<String>> inputList) {
        combinedProductFlavors = new ArrayList<>()
        combinedProductFlavorsMap = new HashMap<>()
        if (inputList == null || inputList.size() == 0) {
            return
        }
        List<Integer> combination = new ArrayList<Integer>();
        int n = inputList.size();
        for (int i = 0; i < n; i++) {
            combination.add(0);
        }
        int i = 0;
        boolean isContinue = true;
        while (isContinue) {
            List<String> items = new ArrayList<>()
            String item = inputList.get(0).get(combination.get(0))
            items.add(item)
            String combined = item
            for (int j = 1; j < n; j++) {
                item = upperCase(inputList.get(j).get(combination.get(j)))
                combined += item
                items.add(item)
            }
            combinedProductFlavors.add(combined)
            combinedProductFlavorsMap.put(combined, items)
            i++;
            combination.set(n - 1, i);
            for (int j = n - 1; j >= 0; j--) {
                if (combination.get(j) >= inputList.get(j).size()) {
                    combination.set(j, 0);
                    i = 0;
                    if (j - 1 >= 0) {
                        combination.set(j - 1, combination.get(j - 1) + 1);
                    }
                }
            }
            isContinue = false;
            for (Integer integer : combination) {
                if (integer != 0) {
                    isContinue = true;
                }
            }
        }
    }

    def upperCase(String str) {
        char[] ch = str.toCharArray()
        if (ch[0] >= 'a' && ch[0] <= 'z') {
            ch[0] -= 32
        }
        return String.valueOf(ch)
    }

}