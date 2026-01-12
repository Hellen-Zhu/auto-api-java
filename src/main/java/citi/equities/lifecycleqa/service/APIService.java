package citi.equities.lifecycleqa.service;

import citi.equities.lifecycleqa.common.entities.SuiteParameter;

import java.util.List;
import java.util.Map;

public interface APIService {

    void run(List<SuiteParameter> list);

    List<SuiteParameter> buildSuiteParameterListForIdRun(String id, String profile);

    List<SuiteParameter> buildSuiteParameterListForComponentRun(Map<String, String> info);

    void sendCompleteSignalForModule(String groupId);
}
