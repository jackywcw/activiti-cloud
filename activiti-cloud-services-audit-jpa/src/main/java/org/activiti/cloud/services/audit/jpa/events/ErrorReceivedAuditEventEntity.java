/*
 * Copyright 2018 Alfresco, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.cloud.services.audit.jpa.events;

import org.activiti.api.process.model.BPMNError;
import org.activiti.cloud.api.process.model.events.CloudBPMNErrorReceivedEvent;
import org.activiti.cloud.services.audit.jpa.converters.json.ErrorJpaJsonConverter;
import org.hibernate.annotations.Type;

import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;

@Entity
@DiscriminatorValue(value = ErrorReceivedAuditEventEntity.ERROR_RECEIVED_EVENT)
public class ErrorReceivedAuditEventEntity extends AuditEventEntity {

    protected static final String ERROR_RECEIVED_EVENT = "ErrorReceivedEvent";
    
    @Convert(converter = ErrorJpaJsonConverter.class)
    @Lob
    private BPMNError error;

    public ErrorReceivedAuditEventEntity() {
    }

    public ErrorReceivedAuditEventEntity(CloudBPMNErrorReceivedEvent cloudEvent) {
        super(cloudEvent);
        setError(cloudEvent.getEntity()) ;
        if (error != null) {
            setProcessDefinitionId(error.getProcessDefinitionId());
            setProcessInstanceId(error.getProcessInstanceId());
            setEntityId(error.getElementId());
        }
    }
    
    public BPMNError getError() {
        return error;
    }

    public void setError(BPMNError error) {
        this.error = error;
    }
}
