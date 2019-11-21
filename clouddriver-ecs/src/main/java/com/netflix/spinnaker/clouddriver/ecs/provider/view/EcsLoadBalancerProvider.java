/*
 * Copyright 2018 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS;

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.data.ArnUtils;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsTargetGroupCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<EcsLoadBalancerCache> {

  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;
  private final EcsAccountMapper ecsAccountMapper;
  private final ServiceCacheClient ecsServiceCacheClient;
  private final EcsTargetGroupCacheClient ecsTargetGroupCacheClient;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public EcsLoadBalancerProvider(
      EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient,
      EcsAccountMapper ecsAccountMapper,
      ServiceCacheClient ecsServiceCacheClient,
      EcsTargetGroupCacheClient ecsTargetGroupCacheClient) {
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
    this.ecsAccountMapper = ecsAccountMapper;
    this.ecsServiceCacheClient = ecsServiceCacheClient;
    this.ecsTargetGroupCacheClient = ecsTargetGroupCacheClient;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    Map<String, EcsLoadBalancerSummary> map = new HashMap<>();
    List<EcsLoadBalancerCache> loadBalancers = ecsLoadbalancerCacheClient.findAll();

    for (EcsLoadBalancerCache lb : loadBalancers) {
      if (lb.getAccount() == null) {
        continue;
      }

      String name = lb.getLoadBalancerName();
      String region = lb.getRegion();

      EcsLoadBalancerSummary summary = map.get(name);
      if (summary == null) {
        summary = new EcsLoadBalancerSummary().withName(name);
        map.put(name, summary);
      }

      EcsLoadBalancerDetail loadBalancer = new EcsLoadBalancerDetail();
      loadBalancer.setAccount(lb.getAccount());
      loadBalancer.setRegion(region);
      loadBalancer.setName(name);
      loadBalancer.setVpcId(lb.getVpcId());
      loadBalancer.setSecurityGroups(lb.getSecurityGroups());
      loadBalancer.setLoadBalancerType(lb.getLoadBalancerType());
      loadBalancer.setTargetGroups(lb.getTargetGroups());

      summary
          .getOrCreateAccount(lb.getAccount())
          .getOrCreateRegion(region)
          .getLoadBalancers()
          .add(loadBalancer);
    }

    return new ArrayList<>(map.values());
  }

  @Override
  public Item get(String name) {
    // unused method
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    // unused method
    throw new UnsupportedOperationException("Not implemented.");
  }

  @Override
  public Set<EcsLoadBalancerCache> getApplicationLoadBalancers(String application) {
    // Find the load balancers currently in use by ECS services in this application
    Set<Service> services =
        ecsServiceCacheClient.getAll().stream()
            .filter(service -> service.getApplicationName().equals(application))
            .collect(Collectors.toSet());

    Collection<String> allTargetGroupKeys = ecsTargetGroupCacheClient.getAllKeys();
    Set<String> targetGroupKeys = new HashSet<>();

    // find all the target group cache keys
    for (Service service : services) {
      String awsAccountName =
          ecsAccountMapper.fromEcsAccountNameToAwsAccountName(service.getAccount());
      for (LoadBalancer loadBalancer : service.getLoadBalancers()) {
        if (loadBalancer.getTargetGroupArn() != null) {
          String keyPrefix =
              String.format(
                  "%s:%s:%s:%s:%s:",
                  AmazonCloudProvider.ID,
                  TARGET_GROUPS.getNs(),
                  awsAccountName,
                  service.getRegion(),
                  ArnUtils.extractTargetGroupName(loadBalancer.getTargetGroupArn()).get());
          Set<String> matchingKeys =
              allTargetGroupKeys.stream()
                  .filter(key -> key.startsWith(keyPrefix))
                  .collect(Collectors.toSet());
          targetGroupKeys.addAll(matchingKeys);
        }
      }
    }

    // find the load balancers for all the target groups
    List<EcsLoadBalancerCache> tgLBs =
        ecsLoadbalancerCacheClient.findWithTargetGroups(targetGroupKeys);

    // TODO CLARE associate server groups and target health with load balancer results

    return new HashSet<>(tgLBs);
  }
}
