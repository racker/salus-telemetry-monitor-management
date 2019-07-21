/*
 *    Copyright 2019 Rackspace US, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *
 */

package com.rackspace.salus.monitor_management.config;

import javax.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
public class TxnConfig {
//  // @Bean
//  // @Autowired
//  // public HibernateTransactionManager transactionManager() {
//
//  //   return txManager;
//  // }
//
//  @Autowired
//  private EntityManagerFactory entityManagerFactory;
//
//  static class DummyTransactionManager implements PlatformTransactionManager {
//    public TransactionStatus getTransaction(@Nullable TransactionDefinition var1)
//    {return null;}
//
//    public void commit(TransactionStatus var1){}
//
//    public void rollback(TransactionStatus var1){}
//  }
//
//// @Bean(name = "transactionManager")
////     public ChainedKafkaTransactionManager transactionManager(
////         @Qualifier("kafkaTransactionManager") PlatformTransactionManager ds2) {
//
////   if (entityManagerFactory.unwrap(SessionFactory.class) == null) {
////     throw new NullPointerException("factory is not a hibernate factory");
////   }
//
////   SessionFactory   sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
//
////   HibernateTransactionManager txManager = new HibernateTransactionManager();
////   txManager.setSessionFactory(sessionFactory);
//
////   return new ChainedKafkaTransactionManager(txManager, ds2);
////     }
//
//
//// @Bean(name = "transactionManager")
////     public PlatformTransactionManager transactionManager(@Qualifier("kafkaTransactionManager") KafkaTransactionManager ds1) {
////       return ds1;
//
////     }
//
//  private KafkaTemplate<String,Object> kafkaTemplate;
//
////  @Bean(name = "oldtransactionManager2")
////  public KafkaTransactionManager kafkaTransactionManager(
////      ProducerFactory<Object, Object> kafkaProducerFactory) {
////    KafkaTransactionManager ktm = new KafkaTransactionManager(kafkaProducerFactory);;
////    ktm.setTransactionSynchronization(AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
////    return ktm;
////  }
//
@Bean(name = "transactionManager")

public JpaTransactionManager transactionManager(EntityManagerFactory em) {
    return new JpaTransactionManager(em);
}

@Bean(name = "chainedTransactionManager")
public ChainedTransactionManager chainedTransactionManager(JpaTransactionManager transactionManager,
                                                           KafkaTransactionManager kafkaTransactionManager) {
    return new ChainedTransactionManager(kafkaTransactionManager, transactionManager);
}
}
