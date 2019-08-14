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

import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;

  @Autowired
  public EcsLoadBalancerProvider(EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient) {
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
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
  public Set<AmazonLoadBalancer> getApplicationLoadBalancers(String application) {
    return null; // TODO - Implement this.  This is used to show load balancers and reveals other
    // buttons
  }
}
