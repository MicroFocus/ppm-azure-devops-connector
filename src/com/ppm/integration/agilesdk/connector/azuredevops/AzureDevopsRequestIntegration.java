/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hp.ppm.common.model.AgileEntityIdName;
import com.ppm.integration.agilesdk.connector.azuredevops.model.Field;
import com.ppm.integration.agilesdk.connector.azuredevops.model.WorkItem;
import com.ppm.integration.agilesdk.connector.azuredevops.model.WorkItemType;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsService;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsServiceProvider;
import com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils;
import org.apache.commons.lang.StringUtils;
import com.ppm.integration.agilesdk.connector.azuredevops.util.AgileEntityUtils;
import com.hp.ppm.common.model.AgileEntityIdProjectDate;
import com.hp.ppm.integration.model.AgileEntityFieldValue;
import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.dm.DataField.DATA_TYPE;
import com.ppm.integration.agilesdk.dm.RequestIntegration;
import com.ppm.integration.agilesdk.model.AgileEntity;
import com.ppm.integration.agilesdk.model.AgileEntityFieldInfo;
import com.ppm.integration.agilesdk.model.AgileEntityInfo;

public class AzureDevopsRequestIntegration extends RequestIntegration {

    private static final int ISSUES_BATCH_SIZE = 200;

    @Override
    public List<AgileEntityInfo> getAgileEntitiesInfo(String agileProjectValue, ValueSet instanceConfigurationParameters) {

        List<WorkItemType> workItemTypes = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getWorkItemTypesForProject(agileProjectValue);

        List<AgileEntityInfo> entityList = new ArrayList<AgileEntityInfo>();
        for (WorkItemType workItemType : workItemTypes) {
            AgileEntityInfo feature = new AgileEntityInfo();
            feature.setName(workItemType.getName());
            feature.setType(workItemType.getName()); // There is not ID for the Work Item type, the name is used as identifier.
            entityList.add(feature);
        }

        Collections.sort(entityList, new Comparator<AgileEntityInfo>() {
            @Override
            public int compare(AgileEntityInfo o1, AgileEntityInfo o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        return entityList;
    }

    @Override
    public List<AgileEntityFieldInfo> getAgileEntityFieldsInfo(String agileProjectValue, String entityType,
                                                               ValueSet instanceConfigurationParameters) {
        List<AgileEntityFieldInfo> fieldsInfo = new ArrayList<>();

        List<Field> fields = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getFieldsDetails(agileProjectValue, entityType);

        for (Field field : fields) {

            if (StringUtils.isBlank(field.getType())
                    || "identity".equals(field.getType())
                    || "history".equals(field.getType())) {
                // We don't support mapping these types of fields.
                continue;
            }

            AgileEntityFieldInfo fieldInfo = new AgileEntityFieldInfo();
            fieldInfo.setMultiValue(false); // We don't support multi-value fields for now.
            fieldInfo.setId(field.getReferenceName());
            fieldInfo.setLabel(field.getName());
            if (field.getAllowedValues() != null && field.getAllowedValues().length > 0) {
                fieldInfo.setListType(true);
            } else {
                fieldInfo.setListType(false);
            }

            fieldInfo.setFieldType(AgileEntityUtils.getAgileFieldType(field).name());

            fieldsInfo.add(fieldInfo);
        }

        // We sort fields alphabetically.
        Collections.sort(fieldsInfo, new Comparator<AgileEntityFieldInfo>() {
            @Override
            public int compare(AgileEntityFieldInfo o1, AgileEntityFieldInfo o2) {
                return o1.getLabel().compareToIgnoreCase(o2.getLabel());
            }
        });

        return fieldsInfo;
    }


    @Override
    public List<AgileEntityFieldValue> getAgileEntityFieldsValueList(final String agileProjectValue,
                                                                     final String entityType, final ValueSet instanceConfigurationParameters, final String fieldName,
                                                                     final boolean isLogicalName) {

        List<Field> fields = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getFieldsDetails(agileProjectValue, entityType);

        for (Field field : fields) {
            if (fieldName.equals(field.getReferenceName()) && field.getAllowedValues() != null) {
                List<AgileEntityFieldValue> values = new ArrayList<>();
                for (String allowedValue: field.getAllowedValues()) {
                    AgileEntityFieldValue fieldValue = new AgileEntityFieldValue();
                    fieldValue.setId(allowedValue);
                    fieldValue.setName(allowedValue);
                    values.add(fieldValue);
                }
                return values;
            }
        }

        // Field not found.
        return new ArrayList<AgileEntityFieldValue>();
    }


    /**
     * @return An AgileEntity with only the ID, URL and LastUpdateDate populated.
     */
    @Override
    public AgileEntity updateEntity(String agileProjectValue, String entityType, AgileEntity entity,
                                    ValueSet instanceConfigurationParameters) {

        AzureDevopsService service = AzureDevopsServiceProvider.get(instanceConfigurationParameters);

        WorkItem updatedWorkIem = service.updateWorkItem(agileProjectValue, entity.getId(), entity.getAllFields());

        // We only care about id, URL and last UpdateTime in the returned object.
        AgileEntity ae = new AgileEntity();
        ae.setEntityUrl(service.getWorkItemUrl(updatedWorkIem.getId()));
        ae.setId(updatedWorkIem.getId());
        ae.setLastUpdateTime(updatedWorkIem.getLastUpdateTime());

        return ae;
    }

    /**
     * @return The created AgileEntity with only the ID, URL and LastUpdateDate populated. Fields are not included.
     */
    @Override
    public AgileEntity createEntity(String agileProjectValue, String entityType, AgileEntity entity,
                                    ValueSet instanceConfigurationParameters) {

        AzureDevopsService service = AzureDevopsServiceProvider.get(instanceConfigurationParameters);

        // First we create a new work item
        WorkItem wi = service.createWorkItem(agileProjectValue, entityType);
        // We set the id of the created work item in the passed AgileEntity.
        entity.setId(wi.getId());

        // Then we update all the fields.
        // We only care about id, URL and last UpdateTime in the returned object.
        return updateEntity(agileProjectValue, entityType, entity, instanceConfigurationParameters);
    }

    @Override
    public List<AgileEntity> getEntities(String agileProjectValue, String entityType,
                                         ValueSet instanceConfigurationParameters, Set<String> entityIds, Date modifiedSinceDate) {

        AzureDevopsService service = AzureDevopsServiceProvider.get(instanceConfigurationParameters);

        if (entityIds == null || entityIds.isEmpty()) {
            return new ArrayList<AgileEntity>();
        }

        final List<Field> fieldsInfo  = service.getFieldsDetails(agileProjectValue, entityType);

        List<AgileEntity> entities = new ArrayList<>(entityIds.size());

        List<List<String>> batchedEntityIds = com.google.common.collect.Lists.partition(new ArrayList<>(entityIds), ISSUES_BATCH_SIZE);

        for (List<String> entityIdsBatch : batchedEntityIds) {
            List<WorkItem> workItems = service.getWorkItemsModifiedSince(agileProjectValue, entityType, entityIdsBatch, modifiedSinceDate);
            entities.addAll(workItems.stream().map(wi -> AgileEntityUtils.workItemToAgileEntity(wi, service, fieldsInfo)).collect(Collectors.toList()));
        }

        return entities;
    }

    @Override
    public AgileEntity getEntity(String agileProjectValue, String entityType,
                                 ValueSet instanceConfigurationParameters, String entityId) {
        if (StringUtils.isBlank(entityId)) {
            return null;
        }

        AzureDevopsService service = AzureDevopsServiceProvider.get(instanceConfigurationParameters);

        return AgileEntityUtils.workItemToAgileEntity(service.getSingleWorkItem(entityId), service, service.getFieldsDetails(agileProjectValue, entityType));
    }

    @Override
    /** @since 10.0.3 */
    public boolean supportsAgileEntityToNewPPMRequestSync() {
        return true;
    }

    @Override
    /** @since 10.0.3 */
    public List<AgileEntityIdProjectDate> getAgileEntityIDsToCreateInPPM(final String agileProjectValue, final String entityType,
                                                                         final ValueSet instanceConfigurationParameters, Date createdSinceDate) {

        // We have no way to know if some of the issues in AzureDevops are already mapped in PPM, so we'll return all of them.
        if (agileProjectValue == null || entityType.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgileEntityIdProjectDate> entities = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getAgileEntityIdsCreatedSince(agileProjectValue, entityType, createdSinceDate);

        return entities;

    }

    @Override
    /** @since 10.0.3 */
    public boolean supportsPPMRequestToExistingAgileEntitySync() {
        return true;
    }

    @Override
    /** @since 10.0.3 */
    public List<AgileEntityIdName> getCandidateEntitiesToSyncWithRequests(final String agileProjectValue, final String entityType,
                                                                          final ValueSet instanceConfigurationParameters) {
        List<AgileEntityIdName> entities = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getAgileEntityIdsAndNames(agileProjectValue, entityType);

        return entities;
    }

}
