/*
 * /*
 *  * Copyright 2007,2016 EUROPEAN UNION
 *  * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 *  * the European Commission - subsequent versions of the EUPL (the "Licence");
 *  * You may not use this work except in compliance with the Licence.
 *  * You may obtain a copy of the Licence at:
 *  *
 *  * https://ec.europa.eu/isa2/solutions/european-union-public-licence-eupl_en
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the Licence is distributed on an "AS IS" basis,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the Licence for the specific language governing permissions and
 *  * limitations under the Licence.
 *  *
 *  * Date: 2020/05/11
 *  * Authors:
 *  * European Commission, Joint Research Centre - jrc-inspire-support@ec.europa.eu
 *  * National Land Survey of Finland, SDI Services - inspire@nls.fi
 *  *
 *  * This work was supported by the Interoperability solutions for public
 *  * administrations, businesses and citizens programme (http://ec.europa.eu/isa2)
 *  * through Action 2016.10: European Location Interoperability Solutions
 *  * for e-Government (ELISE)
 */
package eu.europa.ec.re3gistry2.restapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.NoResultException;

import eu.europa.ec.re3gistry2.base.utility.BaseConstants;
import eu.europa.ec.re3gistry2.base.utility.Configuration;
import eu.europa.ec.re3gistry2.crudimplementation.RegFieldManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegFieldmappingManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegItemManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegItemclassManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegItemhistoryManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegLocalizationManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegRelationManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegRelationpredicateManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegStatusManager;
import eu.europa.ec.re3gistry2.crudimplementation.RegStatuslocalizationManager;
import eu.europa.ec.re3gistry2.model.RegField;
import eu.europa.ec.re3gistry2.model.RegFieldmapping;
import eu.europa.ec.re3gistry2.model.RegItem;
import eu.europa.ec.re3gistry2.model.RegItemclass;
import eu.europa.ec.re3gistry2.model.RegItemhistory;
import eu.europa.ec.re3gistry2.model.RegLanguagecode;
import eu.europa.ec.re3gistry2.model.RegLocalization;
import eu.europa.ec.re3gistry2.model.RegRelation;
import eu.europa.ec.re3gistry2.model.RegRelationpredicate;
import eu.europa.ec.re3gistry2.model.RegStatus;
import eu.europa.ec.re3gistry2.model.RegStatusgroup;
import eu.europa.ec.re3gistry2.model.RegStatuslocalization;
import eu.europa.ec.re3gistry2.restapi.model.BasicContainedItem;
import eu.europa.ec.re3gistry2.restapi.model.ContainedItem;
import eu.europa.ec.re3gistry2.restapi.model.Item;
import eu.europa.ec.re3gistry2.restapi.model.ItemClass;
import eu.europa.ec.re3gistry2.restapi.model.ItemRef;
import eu.europa.ec.re3gistry2.restapi.model.LocalizedProperty;
import eu.europa.ec.re3gistry2.restapi.model.LocalizedPropertyValue;
import eu.europa.ec.re3gistry2.restapi.model.VersionInformation;
import eu.europa.ec.re3gistry2.restapi.util.StatusLocalization;
import java.text.SimpleDateFormat;
import javax.persistence.EntityManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fetch RegItems from DB and convert them to Item objects
 */
public class ItemSupplier {

    private static final Logger LOG = LogManager.getLogger(ItemSupplier.class.getName());

    private static final String TYPE_REGISTRY = BaseConstants.KEY_ITEMCLASS_TYPE_REGISTRY;
    private static final String TYPE_REGISTER = BaseConstants.KEY_ITEMCLASS_TYPE_REGISTER;
    private static final String TYPE_ITEM = BaseConstants.KEY_ITEMCLASS_TYPE_ITEM;

    private final RegItemclassManager regItemClassManager;
    private final RegItemManager regItemManager;
    private final RegItemhistoryManager regItemHistoryManager;
    private final RegRelationManager regRelationManager;
    private final RegFieldManager regFieldManager;
    private final RegLocalizationManager reglocalizationManager;
    private final RegFieldmappingManager regFieldmappingManager;
    private final RegStatusManager regStatusManager;
    private final RegStatuslocalizationManager regStatusLocalizationManager;

    private final RegLanguagecode masterLanguage;
    private final RegLanguagecode languageCode;

    private final RegRelationpredicate hasRegistry;
    private final RegRelationpredicate hasRegister;
    private final RegRelationpredicate hasParent;
    private final RegRelationpredicate hasCollection;
    private final RegRelationpredicate hasSuccessor;
    private final RegRelationpredicate hasPredecessor;
    private final RegRelationpredicate hasReference;

    // Lazily populated
    private RegField labelField;
    private Map<String, String> fieldToLabel;
    private Map<String, StatusLocalization> statusToLocalization;
    private Map<String, List<RegFieldmapping>> itemclassToFieldmapping;
    private boolean topConceptItem = false;

    public ItemSupplier(EntityManager em,
            RegLanguagecode masterLanguage,
            RegLanguagecode languageCode) throws Exception {

        this.regItemClassManager = new RegItemclassManager(em);
        this.regItemManager = new RegItemManager(em);
        this.regItemHistoryManager = new RegItemhistoryManager(em);
        RegRelationpredicateManager relationPredicateManager = new RegRelationpredicateManager(em);
        this.regRelationManager = new RegRelationManager(em);
        this.regFieldManager = new RegFieldManager(em);
        this.reglocalizationManager = new RegLocalizationManager(em);
        this.regFieldmappingManager = new RegFieldmappingManager(em);
        this.regStatusManager = new RegStatusManager(em);
        this.regStatusLocalizationManager = new RegStatuslocalizationManager(em);

        this.masterLanguage = masterLanguage;
        this.languageCode = languageCode;

        this.hasRegistry = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_REGISTRY);
        this.hasRegister = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_REGISTER);
        this.hasParent = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_PARENT);
        this.hasCollection = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_COLLECTION);
        this.hasSuccessor = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_SUCCESSOR);
        this.hasPredecessor = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_PREDECESSOR);
        this.hasReference = relationPredicateManager.get(BaseConstants.KEY_PREDICATE_REFERENCE);
    }

    public Item getItemByUuid(String uuid) throws Exception {
        // Not possible to request specific version with uuid
        return toItem(regItemManager.get(uuid));
    }

    public Item getItem(RegItem regItem) throws Exception {
        // Not possible to request specific version with uuid
        return toItem(regItem);
    }

    public Item getItemByUri(String uri) throws Exception {
        RegItem item = getRegItemByUri(uri);
        if (item == null) {
            return null;
        }
        return toItem(item);
    }

    private RegItem getRegItemByUri(String uri) throws Exception {
        int i = uri.lastIndexOf('/');
        if (i < 0) {
            throw new NoResultException();
        }
        String localid = uri.substring(i + 1);
        try {
            int uriCollection = uri.substring(0, i).lastIndexOf('/');
            String regItemClassLocalId = uri.substring(uriCollection + 1).replace("/" + localid, "");
            RegItemclass parentClass = regItemClassManager.get(regItemClassLocalId);
            RegItemclass regItemRegItemClass = regItemClassManager.getChildItemclass(parentClass).get(0);

            RegItem regItem = regItemManager.getByLocalidAndRegItemClass(localid, regItemRegItemClass);
            if (uri.equals(getURI(regItem))) {
                return regItem;
            }
        } catch (Exception e) {
            for (RegItem item : regItemManager.getByLocalid(localid)) {
                if (uri.equals(getURI(item))) {
                    return item;
                }
            }
        }
        return null;
    }

    private Item toItem(RegItem regItem) throws Exception {
        if (!regItem.getRegStatus().getIspublic()) {
            return null;
        }

        Item item = new Item();
        setMainPropertiesForRegItem(regItem, item);

        setRegistryAndRegisterItemRef(regItem, item);
        setIsDefinedByFromRegItem(regItem, item);
        setContainedItemsFromRegItem(regItem, item);

        setContainedItemsFromRegItemClassWithParent(regItem, item);

        setTopConceptsFromRegItem(regItem, item);
        setInSchemeAndTopConceptOfFromRegItem(regItem, item);
        setNarrowerFromRegItem(regItem, item);
        setBroaderFromRegItem(regItem, item);

        return item;
    }

    protected ContainedItem toContainedItem(RegItem regItem) throws Exception {
        ContainedItem containedItem = new ContainedItem();

        setMainPropertiesForRegItem(regItem, containedItem);
        setRegistryAndRegisterItemRef(regItem, containedItem);
        setIsDefinedByFromRegItem(regItem, containedItem);

        setTopConceptsFromRegItem(regItem, containedItem);
        setInSchemeAndTopConceptOfFromRegItem(regItem, containedItem);
        if (topConceptItem) {
            setNarrowerFromRegItem(regItem, containedItem);
            setBroaderFromRegItem(regItem, containedItem);
            setContainedItemsFromRegItem(regItem, containedItem);
        }

        return containedItem;
    }

    protected BasicContainedItem toBasicContainedItem(RegItem regItem) throws Exception {
        BasicContainedItem citem = new ContainedItem();
        citem.setUri(getURI(regItem));
        return citem;
    }

    protected ContainedItem toBasicContainedItemDefinedBy(RegItem regItem) throws Exception {
        ContainedItem containedItem = new ContainedItem();
        setMainPropertiesForRegItem(regItem, containedItem);
        return containedItem;
    }

    protected ContainedItem toContainedItemWithoutItems(RegItem regItem) throws Exception {
        ContainedItem containedItem = new ContainedItem();

        setMainPropertiesForRegItem(regItem, containedItem);
        setRegistryAndRegisterItemRef(regItem, containedItem);

        setIsDefinedByFromRegItem(regItem, containedItem);
        setTopConceptsFromRegItem(regItem, containedItem);
        setInSchemeAndTopConceptOfFromRegItem(regItem, containedItem);

        return containedItem;
    }

    private ContainedItem setMainPropertiesForRegItem(RegItem regItem, ContainedItem item) throws Exception {
        item.setUuid(regItem.getUuid());
        item.setUri(getURI(regItem));
        item.setLocalid(regItem.getLocalid());
        item.setLatest(true);
        item.setInsertdate(regItem.getInsertdate());
        item.setEditdate(regItem.getEditdate());
        setVersionAndHistory(regItem, item);
        item.setType(regItem.getRegItemclass().getRegItemclasstype().getLocalid());
        item.setLanguage(languageCode.getIso6391code());
        item.setItemclass(new ItemClass(regItem.getRegItemclass().getLocalid()));
        item.setProperties(getLocalizedProperties(regItem, fieldMapping -> !fieldMapping.getHidden()));

        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:
                topConceptItem = false;
                break;
            case TYPE_REGISTER:
                topConceptItem = false;
                break;
            case TYPE_ITEM:
                topConceptItem = true;
                break;
            default:
                throw new RuntimeException("Unexpected type");
        }
        if (topConceptItem) {
            List<RegItem> hasCollectionList = getRelatedItemsBySubject(regItem, hasCollection);
            if (hasCollectionList != null && !hasCollectionList.isEmpty()) {
                item.setHasCollection(true);
            }
            List<RegItem> isParentList = getRelatedItemsBySubject(regItem, hasParent);
            if (isParentList != null && !isParentList.isEmpty()) {
                item.setIsParent(true);
            }
            if (regItem.getExternal()) {
                item.setExternal(true);
            }
        }
        return item;
    }

    private void setVersionAndHistory(RegItem regItem, ContainedItem item) throws Exception {
        String uri = item.getUri();

        List<RegItemhistory> itemHistory = regItemHistoryManager.getByRegItemReference(regItem);

        // Requested current version
        int maxVersionNumber = itemHistory.stream()
                .mapToInt(ih -> ih.getVersionnumber())
                .max()
                .orElse(1); // Default to 1 if for whatever reason we can not find max version
        int thisversion = itemHistory.size() + 1;
        item.setVersion(new VersionInformation(thisversion, uri + ":" + thisversion));
        item.setVersionHistory(itemHistory.stream()
                .filter(ih -> ih.getVersionnumber() != maxVersionNumber + 1)
                .map(ih -> new VersionInformation(ih.getVersionnumber(), uri + ":" + ih.getVersionnumber()))
                .collect(Collectors.toList()));
    }

    private void setRegistryAndRegisterItemRef(RegItem regItem, ContainedItem item) throws Exception {
        ItemRef registryRef = null;
        ItemRef registerRef = null;

        switch (item.getType()) {
            case TYPE_REGISTRY:
                registerRef = null;

                break;
            case TYPE_REGISTER:
                registryRef = toItemRef(getRelatedItemBySubject(regItem, hasRegistry));

                // Making an assumption that register can not be parent or collection to any item
                break;
            case TYPE_ITEM:
                registryRef = toItemRef(getRelatedItemBySubject(regItem, hasRegistry));
                registerRef = toItemRef(getRelatedItemBySubject(regItem, hasRegister));

                break;
            default:
                throw new RuntimeException("Unexpected type");
        }
        if (registryRef != null) {
            item.setRegistry(registryRef);
        }
        if (registerRef != null) {
            item.setRegister(registerRef);
        }
    }

    private void setContainedItemsFromRegItem(RegItem regItem, ContainedItem item) throws Exception {
        List<ContainedItem> containedItems = new ArrayList<>();
        List<RegItem> containedItemsList = null;
        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:
                containedItemsList = getRegisters(regItem);

                if (containedItemsList != null && !containedItemsList.isEmpty()) {
                    for (RegItem containedItem : containedItemsList) {

                        if (!containedItem.getRegItemclass().getSystemitem()) {
                            containedItems.add(toContainedItemWithoutItems(containedItem));
                        }
                    }
                    if (!containedItems.isEmpty()) {
                        item.setContainedItems(containedItems);
                    }
                }
                break;
            case TYPE_REGISTER:
                containedItemsList = getDirectlyContainedItemsOfRegister(regItem);

                if (containedItemsList != null && !containedItemsList.isEmpty()) {
                    for (RegItem containedItem : containedItemsList) {

                        if (!containedItem.getRegItemclass().getSystemitem()) {
                            containedItems.add(toContainedItemWithoutItems(containedItem));
                        }
                    }
                    if (!containedItems.isEmpty()) {
                        item.setContainedItems(containedItems);
                    }
                }
                break;
            case TYPE_ITEM:
                List<BasicContainedItem> topConcepts = new ArrayList<>();
                try {
                    containedItemsList = new ArrayList<>();
//                    if (complexItem) {
//                        containedItemsList = getRelatedItemsByObject(regItem, hasCollection);
//                    } else {
                    List<String> collectionNoParentList = getAllColectionsNoParentOfItem(regItem);
                    for (String uuid : collectionNoParentList) {
                        containedItemsList.add(regItemManager.get(uuid));
                    }
                    if (containedItemsList == null || containedItemsList.isEmpty()) {
                        containedItemsList = getRelatedItemsByObject(regItem, hasCollection);
                    }
                    if (containedItemsList == null || containedItemsList.isEmpty()) {
                        containedItemsList = getRelatedItemsByObject(regItem, hasParent);
                    }
//                    }
                    for (RegItem childItem : containedItemsList) {
                        if (!childItem.getRegItemclass().getSystemitem()) {
                            topConcepts.add(toBasicContainedItem(childItem));
                        }
                    }
                } catch (Exception exception) {
                    for (RegItem childItem : getRelatedItemsByObject(regItem, hasParent)) {
                        if (!childItem.getRegItemclass().getSystemitem()) {
                            topConcepts.add(toBasicContainedItem(childItem));
                        }
                    }
                }
                if (!topConcepts.isEmpty()) {
                    item.setTopConcepts(topConcepts);
                }

                if (containedItemsList != null && !containedItemsList.isEmpty()) {
                    for (RegItem containedItem : containedItemsList) {

                        if (!containedItem.getRegItemclass().getSystemitem()) {
                            containedItems.add(toContainedItem(containedItem));
                        }
                    }
                    if (!containedItems.isEmpty()) {
                        item.setContainedItems(containedItems);
                    }
                }
                break;
            default:
                throw new RuntimeException("Unexpected type");
        }

    }

    private void setContainedItemsFromRegItemClassWithParent(RegItem regItem, ContainedItem item) throws Exception {
        List<ContainedItem> containedItems = new ArrayList<>();
        List<RegItem> containedItemsList = new ArrayList<>();
        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:

                //theme register, codelist register
                List<RegItemclass> childItemClassList = regItemClassManager.getChildItemclass(regItem.getRegItemclass());

                List<RegItemclass> itemclassWithChildren = new ArrayList<>();
                for (RegItemclass regItemclass : childItemClassList) {
                    if (!regItemclass.getSystemitem()) {
                        //theme am, codelist
                        List<RegItemclass> childofChildItemClassList = regItemClassManager.getChildItemclass(regItemclass);
                        List<RegItemclass> grandchildItemClassList = new ArrayList<>();
                        for (RegItemclass childofChildItemClass : childofChildItemClassList) {
                            //codelistvalues
                            grandchildItemClassList = regItemClassManager.getChildItemclass(childofChildItemClass);
                            if (!grandchildItemClassList.isEmpty()) {
                                break;
                            }
                        }
                        if (!grandchildItemClassList.isEmpty()) {
                            itemclassWithChildren.addAll(childofChildItemClassList);
                        } else {
                            itemclassWithChildren.add(regItemclass);
                        }
                    }
                }

                for (RegItemclass regItemclass : itemclassWithChildren) {
                    containedItemsList.addAll(regItemManager.getAll(regItemclass));
                }

                if (containedItemsList != null && !containedItemsList.isEmpty()) {
                    for (RegItem containedItem : containedItemsList) {

                        if (!containedItem.getRegItemclass().getSystemitem()) {
                            containedItems.add(toContainedItemWithoutItems(containedItem));
                        }
                    }
                    if (!containedItems.isEmpty()) {
                        item.setContainedItemsBeeingParentItemClass(containedItems);
                    }
                }
                break;
            case TYPE_REGISTER:
                break;
            case TYPE_ITEM:
                break;
            default:
                throw new RuntimeException("Unexpected type");
        }

    }

    private void setInSchemeAndTopConceptOfFromRegItem(RegItem regItem, ContainedItem containedItem) throws Exception {

        BasicContainedItem basicContainedItem = null;
        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:

                break;
            case TYPE_REGISTER:
                RegItem relatedRegistry = getRelatedItemBySubject(regItem, hasRegistry);
                basicContainedItem = toBasicContainedItem(relatedRegistry);
                break;
            case TYPE_ITEM:
                RegItem relatedCollection = getRelatedItemBySubject(regItem, hasCollection);
                if (relatedCollection != null) {
                    RegItem parentCollection = getRelatedItemBySubject(relatedCollection, hasParent);
                    if (parentCollection != null) {
                        relatedCollection = parentCollection;
                    }
                }
                if (relatedCollection == null) {
                    relatedCollection = getRelatedItemBySubject(regItem, hasRegister);
                }
                basicContainedItem = toBasicContainedItem(relatedCollection);
                break;
            default:
                throw new RuntimeException("Unexpected type");
        }
        if (basicContainedItem != null && basicContainedItem.getUri() != null) {
            containedItem.setInScheme(basicContainedItem);
            containedItem.setTopConceptOf(basicContainedItem);
        }
    }

    private void setBroaderFromRegItem(RegItem regItem, ContainedItem containedItem) throws Exception {
        List<BasicContainedItem> broader = new ArrayList<>();
        List<RegItem> broaderList = null;

        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:

                break;
            case TYPE_REGISTER:

                break;
            case TYPE_ITEM:
                broaderList = getRelatedItemsBySubject(regItem, hasParent);
                if ((broaderList == null || broaderList.isEmpty())) {
                    broaderList = getRelatedItemsBySubject(regItem, hasCollection);
                }
                if ((broaderList == null || broaderList.isEmpty())) {
                    broaderList = getRelatedItemsBySubject(regItem, hasRegister);
                }

                break;
            default:
                throw new RuntimeException("Unexpected type");
        }

        if (broaderList != null && !broaderList.isEmpty()) {
            for (RegItem childItem : broaderList) {
                if (!childItem.getRegItemclass().getSystemitem()) {
                    broader.add(toBasicContainedItem(childItem));
                }
            }
            if (!broader.isEmpty()) {
                containedItem.setBroader(broader);
            }
        }
    }

    private void setNarrowerFromRegItem(RegItem regItem, ContainedItem containedItem) throws Exception {
        List<BasicContainedItem> narrower = new ArrayList<>();
        List<RegItem> narrowerList = null;
        if (regItem != null && containedItem != null) {
            switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
                case TYPE_REGISTRY:

                    break;
                case TYPE_REGISTER:
//                narrowerList = getDirectlyContainedItemsOfRegister(regItem);
                    break;
                case TYPE_ITEM:
                    narrowerList = getRelatedItemsByObject(regItem, hasParent);
                    if (narrowerList == null || narrowerList.isEmpty()) {
                        narrowerList = getRelatedItemsByObject(regItem, hasCollection);
                    }
                    break;
                default:
                    throw new RuntimeException("Unexpected type");
            }

            if (narrowerList != null && !narrowerList.isEmpty()) {
                for (RegItem childItem : narrowerList) {
                    if (!childItem.getRegItemclass().getSystemitem()) {
                        try {
                            narrower.add(toContainedItemWithoutItems(childItem));
                        } catch (Exception ex) {
                            LOG.error("childItem:" + childItem.getUuid() + ", Error:" + ex.getMessage());
                        }
                    }
                }
                if (!narrower.isEmpty()) {
                    containedItem.setNarrower(narrower);
                }
            }
        }
    }

    private void setTopConceptsFromRegItem(RegItem regItem, ContainedItem containedItem) throws Exception {
        List<BasicContainedItem> topConcepts = new ArrayList<>();
        List<RegItem> childItemList = new ArrayList<>();

        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:
                childItemList = getRegisters(regItem);

                break;
            case TYPE_REGISTER:
                childItemList = getDirectlyContainedItemsOfRegister(regItem);

                break;
            case TYPE_ITEM:
                List<String> collectionNoParentList = getAllColectionsNoParentOfItem(regItem);
                for (String uuid : collectionNoParentList) {
                    childItemList.add(regItemManager.get(uuid));
                }
                if (childItemList == null || childItemList.isEmpty()) {
                    childItemList = getRelatedItemsByObject(regItem, hasCollection);
                }
                if (childItemList == null || childItemList.isEmpty()) {
                    childItemList = getRelatedItemsByObject(regItem, hasParent);
                }

                break;
            default:
                throw new RuntimeException("Unexpected type");
        }

        for (RegItem childItem : childItemList) {
            if (!childItem.getRegItemclass().getSystemitem()) {
                topConcepts.add(toBasicContainedItem(childItem));
            }
        }

        if (!topConcepts.isEmpty()) {
            containedItem.setTopConcepts(topConcepts);
        }
    }

    private void setIsDefinedByFromRegItem(RegItem regItem, ContainedItem containedItem) throws Exception {
        List<ContainedItem> isDefinedBy = new ArrayList<>();

        switch (regItem.getRegItemclass().getRegItemclasstype().getLocalid()) {
            case TYPE_REGISTRY:

                break;
            case TYPE_REGISTER:

                break;
            case TYPE_ITEM:
                for (RegItem relatedItem : getRelatedItemsBySubject(regItem, hasReference)) {
                    if (!relatedItem.getRegItemclass().getSystemitem()) {
                        isDefinedBy.add(toBasicContainedItemDefinedBy(relatedItem));
                    }
                }

                break;
            default:
                throw new RuntimeException("Unexpected type");
        }
        if (!isDefinedBy.isEmpty()) {
            containedItem.setIsDefinedBy(isDefinedBy);
        }
    }

    protected ItemRef toItemRef(RegItem regItem) throws Exception {
        String uri = getURI(regItem);

        RegField labelField = getLabelField();
        RegFieldmapping labelFieldmapping = regFieldmappingManager.getByFieldAndItemClass(labelField, regItem.getRegItemclass());

        String lang = languageCode.getIso6391code();
        String id = labelField.getLocalid();
        boolean istitle = labelField.getIstitle();
        int order = labelFieldmapping.getListorder();
        boolean tablevisible = labelFieldmapping.getTablevisible();

        List<RegLocalization> localizations = reglocalizationManager.getAll(labelField, regItem, languageCode);
        if (localizations == null || localizations.isEmpty()) {
            // fallback to master language localization for this field for this reg item
            lang = masterLanguage.getIso6391code();
            localizations = reglocalizationManager.getAll(labelField, regItem, masterLanguage);
        }
        List<LocalizedPropertyValue> values = localizations.stream()
                .map(l -> new LocalizedPropertyValue(l.getValue(), l.getHref()))
                .collect(Collectors.toList());

        LocalizedProperty property = new LocalizedProperty(lang, id, istitle, id, values, order, tablevisible);
        return new ItemRef(uri, Arrays.asList(property));
    }

    private RegField getLabelField() throws Exception {
        if (labelField == null) {
            labelField = regFieldManager.getByLocalid(BaseConstants.KEY_FIELD_MANDATORY_LABEL_LOCALID);
        }
        return labelField;
    }

    protected String getURI(RegItem regItem) throws Exception {
        if (regItem == null) {
            return null;
        }
        RegItemclass itemclass = regItem.getRegItemclass();
        switch (itemclass.getRegItemclasstype().getLocalid()) {
            case BaseConstants.KEY_ITEMCLASS_TYPE_REGISTRY:
                return itemclass.getBaseuri() + "/" + regItem.getLocalid();
            case BaseConstants.KEY_ITEMCLASS_TYPE_REGISTER:
                String baseuri = itemclass.getBaseuri();
                if (baseuri != null) {
                    return baseuri + "/" + regItem.getLocalid();
                }
                String registryURI = getURI(getRelatedItemBySubject(regItem, hasRegistry));
                return registryURI + "/" + regItem.getLocalid();
            case BaseConstants.KEY_ITEMCLASS_TYPE_ITEM:
                String itemURI = null;
                if (regItem.getExternal()) {
                    itemURI = regItem.getLocalid();
                } else {
                    String registerURI = getURI(getRelatedItemBySubject(regItem, hasRegister));
                    List<RegItem> collectionChain = getCollectionChain(regItem);
                    if (collectionChain.isEmpty()) {
                        return registerURI + "/" + regItem.getLocalid();
                    }
                    String collectionsPath = collectionChain.stream()
                            .map(collection -> collection.getLocalid())
                            .collect(Collectors.joining("/"));
                    itemURI = registerURI + "/" + collectionsPath + "/" + regItem.getLocalid();
                }

                return itemURI;
            default:
                throw new RuntimeException("Invalid type");
        }
    }

    private List<RegFieldmapping> getFieldmappings(RegItemclass itemclass) throws Exception {
        if (itemclassToFieldmapping == null) {
            itemclassToFieldmapping = new HashMap<>();
        }
        String key = itemclass.getUuid();
        List<RegFieldmapping> fieldMappings = itemclassToFieldmapping.get(key);
        if (fieldMappings == null) {
            fieldMappings = regFieldmappingManager.getAll(itemclass);
            itemclassToFieldmapping.put(key, fieldMappings);
        }
        return fieldMappings;
    }

    private List<LocalizedProperty> getLocalizedProperties(RegItem regItem, Predicate<RegFieldmapping> fieldmappingFilter) throws Exception {
        List<RegLocalization> localizations = reglocalizationManager.getAll(regItem, languageCode);
        Map<String, List<RegLocalization>> localizationsByField = localizations.stream()
                .collect(Collectors.groupingBy(it -> it.getRegField().getUuid()));

        Map<String, List<RegLocalization>> localizationsByFieldML;
        if (languageCode.equals(masterLanguage)) {
            localizationsByFieldML = null;
        } else {
            localizationsByFieldML = reglocalizationManager.getAll(regItem, masterLanguage).stream()
                    .collect(Collectors.groupingBy(it -> it.getRegField().getUuid()));
        }

        List<RegFieldmapping> fieldmappings = getFieldmappings(regItem.getRegItemclass());
        return fieldmappings.stream()
                .filter(fieldmappingFilter)
                .map(it -> {
                    try {
                        return getLocalizedProperty(it, regItem, localizationsByField, localizationsByFieldML);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private StatusLocalization getLocalizedStatus(RegStatus regStatus) throws Exception {
        if (statusToLocalization == null) {
            statusToLocalization = new HashMap<>();
        }
        String key = regStatus.getUuid();
        StatusLocalization statusLocalization = statusToLocalization.get(key);
        if (statusLocalization == null) {
            RegStatusgroup regStatusgroup = regStatus.getRegStatusgroup();
            RegStatuslocalization statusLoc;
            String lang;
            try {
                statusLoc = regStatusLocalizationManager.get(regStatus, languageCode);
                lang = languageCode.getIso6391code();
            } catch (NoResultException ignore) {
                //  Try masterLanguage
                try {
                    statusLoc = regStatusLocalizationManager.get(regStatus, masterLanguage);
                    lang = masterLanguage.getIso6391code();
                } catch (NoResultException ignore2) {
                    return null;
                }
            }
            String value = statusLoc.getLabel();
            String href = regStatusgroup.getBaseuri() + "/" + regStatusgroup.getLocalid() + "/" + regStatus.getLocalid();
            statusLocalization = new StatusLocalization(lang, value, href);
            statusToLocalization.put(key, statusLocalization);
        }
        return statusLocalization;
    }

    private LocalizedProperty getLocalizedProperty(RegFieldmapping fieldmapping,
            RegItem regItem,
            Map<String, List<RegLocalization>> localizationsByField,
            Map<String, List<RegLocalization>> localizationsByFieldML) throws Exception {
        RegField field = fieldmapping.getRegField();
        String id = field.getLocalid();
        String lang = languageCode.getIso6391code();
        boolean istitle = field.getIstitle();
        String label = getLabelForField(field);
        int order = fieldmapping.getListorder();
        boolean tablevisible = fieldmapping.getTablevisible();

        List<LocalizedPropertyValue> values = Collections.emptyList();

        //Get Property for allowing to return null value fields
        String allowEmptyFields = Configuration.getInstance().getProperties().getProperty(BaseConstants.KEY_ALLOW_NULL_FIELDS, BaseConstants.KEY_BOOLEAN_STRING_FALSE);
        String dateformat = Configuration.getInstance().getProperties().getProperty(BaseConstants.KEY_PROPERTY_DATEFORMAT, BaseConstants.KEY_STANDARD_DATEFORMAT);
        SimpleDateFormat sdf = new SimpleDateFormat(dateformat);

        switch (field.getRegFieldtype().getUuid()) {
            case BaseConstants.KEY_FIELDTYPE_REGISTRY_UUID:
                return null;
            case BaseConstants.KEY_FIELDTYPE_REGISTER_UUID:
                return null;
            case BaseConstants.KEY_FIELDTYPE_COLLECTION_UUID:
                RegItem collection = getRelatedItemBySubject(regItem, hasCollection);

                LocalizedProperty linksToRelatedItemsCollection = getLinksToRelationItems(field, label, order, tablevisible, collection);

                // Handling the cases in which the values are null and the flag to display null values is true/false
                if (linksToRelatedItemsCollection == null && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return null;
                } else if (linksToRelatedItemsCollection == null && allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
                } else {
                    return linksToRelatedItemsCollection;
                }
            case BaseConstants.KEY_FIELDTYPE_PARENT_UUID:
                RegItem parent = getRelatedItemBySubject(regItem, hasParent);
                LocalizedProperty linksToRelatedItemsParent = getLinksToRelationItems(field, label, order, tablevisible, parent);

                // Handling the cases in which the values are null and the flag to display null values is true/false
                if (linksToRelatedItemsParent == null && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return null;
                } else if (linksToRelatedItemsParent == null && allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
                } else {
                    return linksToRelatedItemsParent;
                }
            case BaseConstants.KEY_FIELDTYPE_SUCCESSOR_UUID:
                RegItem successor = getRelatedItemBySubject(regItem, hasSuccessor);
                LocalizedProperty linksToRelatedItemsSuccessor = getLinksToRelationItems(field, label, order, tablevisible, successor);

                // Handling the cases in which the values are null and the flag to display null values is true/false
                if (linksToRelatedItemsSuccessor == null && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return null;
                } else if (linksToRelatedItemsSuccessor == null && allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
                } else {
                    return linksToRelatedItemsSuccessor;
                }
            case BaseConstants.KEY_FIELDTYPE_PREDECESSOR_UUID:
                RegItem predecessor = getRelatedItemBySubject(regItem, hasPredecessor);
                LocalizedProperty linksToRelatedItemsPredecessor = getLinksToRelationItems(field, label, order, tablevisible, predecessor);

                // Handling the cases in which the values are null and the flag to display null values is true/false
                if (linksToRelatedItemsPredecessor == null && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return null;
                } else if (linksToRelatedItemsPredecessor == null && allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
                } else {
                    return linksToRelatedItemsPredecessor;
                }
            case BaseConstants.KEY_FIELDTYPE_RELATIONREFERENCE_UUID:
                LocalizedProperty linksToRelatedItems = getLinksToRelatedItems(field, label, order, tablevisible, regItem, localizationsByField, localizationsByFieldML);

                // Handling the cases in which the values are null and the flag to display null values is true/false
                if (linksToRelatedItems == null && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return null;
                } else if (linksToRelatedItems == null && allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
                    return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
                } else {
                    return linksToRelatedItems;
                }

            case BaseConstants.KEY_FIELDTYPE_STATUS_UUID:
                StatusLocalization statusLocalization = getLocalizedStatus(fieldmapping.getRegStatus());
                if (statusLocalization == null) {
                    return null;
                }
                lang = statusLocalization.getLang();
                String value = statusLocalization.getValue();
                String href = statusLocalization.getHref();
                values = Collections.singletonList(new LocalizedPropertyValue(value, href));
                break;

            case BaseConstants.KEY_FIELDTYPE_DATECREATION_UUID:

                String dateInsertValue = sdf.format(regItem.getInsertdate());
                values = Collections.singletonList(new LocalizedPropertyValue(dateInsertValue, null));
                break;

            case BaseConstants.KEY_FIELDTYPE_DATEEDIT_UUID:

                if (regItem.getEditdate() != null) {
                    String dateEditValue = sdf.format(regItem.getEditdate());
                    values = Collections.singletonList(new LocalizedPropertyValue(dateEditValue, null));
                }
                break;

            default:
                String key = field.getUuid();
                List<RegLocalization> localizations = localizationsByField.get(key);
                if (localizations == null || localizations.isEmpty()) {
                    if (localizationsByFieldML == null) {
                        break;
                        //return null;
                    }
                    // fallback to master language localization for this field for this reg item
                    lang = masterLanguage.getIso6391code();
                    localizations = localizationsByFieldML.get(key);
                    if (localizations == null) {
                        break;
                        //return null;
                    }
                }
                values = localizations.stream()
                        .map(l -> new LocalizedPropertyValue(l.getValue(), l.getHref()))
                        .collect(Collectors.toList());
                break;
        }

        if (values.isEmpty() && !allowEmptyFields.equals(BaseConstants.KEY_BOOLEAN_STRING_TRUE)) {
            // Don't add properties that have no zero value/href pairs
            return null;
        }
        return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
    }

    private LocalizedProperty getLinksToRelatedItems(RegField field,
            String label, int order, boolean tablevisible, RegItem regItem,
            Map<String, List<RegLocalization>> localizationsByField,
            Map<String, List<RegLocalization>> localizationsByFieldML) throws Exception {
        String id = field.getLocalid();
        boolean istitle = field.getIstitle();
        String lang = languageCode.getIso6391code();

        List<RegLocalization> localizations = localizationsByField.get(field.getUuid());
        if (localizations == null || localizations.isEmpty()) {
            if (localizationsByFieldML == null) {
                return null;
            }
            // fallback to master language localization for this field for this reg item
            lang = masterLanguage.getIso6391code();
            localizations = localizationsByFieldML.get(field.getUuid());
            if (localizations == null) {
                return null;
            }
        }

        List<RegItem> relatedItems = localizations.stream()
                .map(loc -> loc.getRegRelationReference().getRegItemObject())
                .collect(Collectors.toList());
        if (relatedItems.isEmpty()) {
            return null;
        }

        List<LocalizedPropertyValue> values = new ArrayList<>(relatedItems.size());
        for (RegItem relItem : relatedItems) {
            String value = getLabelForItem(relItem);
            if (value == null) {
                continue;
            }
            String href = getURI(relItem);
            values.add(new LocalizedPropertyValue(value, href));
        }

        if (values.isEmpty()) {
            // Don't add properties that have no zero value/href pairs
            return null;
        }

        return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
    }

    private LocalizedProperty getLinksToRelationItems(RegField field,
            String label, int order, boolean tablevisible, RegItem collection) throws Exception {
        String id = field.getLocalid();
        boolean istitle = field.getIstitle();
        String lang = languageCode.getIso6391code();

        List<LocalizedPropertyValue> values = new ArrayList<>();
        String value = getLabelForItem(collection);
        if (value == null) {
            return null;
        }
        String href = getURI(collection);
        values.add(new LocalizedPropertyValue(value, href));

        if (values.isEmpty()) {
            // Don't add properties that have no zero value/href pairs
            return null;
        }

        return new LocalizedProperty(lang, id, istitle, label, values, order, tablevisible);
    }

    private String getLabelForItem(RegItem regItem) throws Exception {
        List<RegLocalization> localizations = reglocalizationManager.getAll(getLabelField(), regItem, languageCode);
        if (localizations == null || localizations.isEmpty()) {
            // fallback to master language localization for this field for this reg item
            localizations = reglocalizationManager.getAll(getLabelField(), regItem, masterLanguage);
        }
        return localizations.stream()
                .findAny()
                .map(l -> l.getValue())
                .orElse(null);
    }

    private String getLabelForField(RegField field) throws Exception {
        if (fieldToLabel == null) {
            fieldToLabel = new HashMap<>();
        }
        String key = field.getUuid();
        String label = fieldToLabel.get(key);
        if (label != null) {
            return label;
        }
        if (label == null) {
            try {
                label = reglocalizationManager.get(field, languageCode).getValue();
            } catch (NoResultException ignore) {
                label = reglocalizationManager.get(field, masterLanguage).getValue();
            }
            fieldToLabel.put(key, label);
        }
        return label;
    }

    private List<RegItem> getRegisters(RegItem registry) throws Exception {
        List<RegItem> registrysRegisters = new ArrayList<>();
        List<RegItem> registers = getAllRegisters();
        List<RegRelation> relations = regRelationManager.getAllByRegItemsSubjectAndPredicate(registers, hasRegistry);
        relations.stream().filter((rel) -> (rel.getRegItemObject().equals(registry))).forEachOrdered((rel) -> {
            registrysRegisters.add(rel.getRegItemSubject());
        });
        return registrysRegisters;
    }

    protected List<RegItem> getAllRegisters() throws Exception {
        return getItemsOfType(TYPE_REGISTER);
    }

    private List<RegItem> getItemsOfType(String classtype) throws Exception {
        List<RegItemclass> allItemClasses = regItemClassManager.getAll();
        List<RegItemclass> itemclasses = allItemClasses.stream()
                .filter(it -> it.getRegItemclasstype().getLocalid().equals(classtype))
                .collect(Collectors.toList());
        List<RegItem> list = new ArrayList<>();
        for (RegItemclass itemclass : itemclasses) {
            list.addAll(regItemManager.getAll(itemclass));
        }
        return list;
    }

    private List<RegItem> getDirectlyContainedItemsOfRegister(RegItem register) throws Exception {
        return regItemManager.getAllSubjectsByRegItemObjectAndPredicateAndSubjectNotPredicate(register, hasRegister, hasCollection);
    }

    private List<String> getAllColectionsNoParentOfItem(RegItem item) throws Exception {
        return regItemManager.getAllItemByRegItemObjectAndPredicateAndSubjectNotPredicate(item, regStatusManager.get("1"), hasCollection, hasParent);
    }

    private List<RegItem> getCollectionChain(RegItem regItem) throws Exception {
        RegItem collection = getRelatedItemBySubject(regItem, hasCollection);
        if (collection == null) {
            return Collections.emptyList();
        }
        LinkedList<RegItem> collectionChain = new LinkedList<>();
        while (collection != null) {
            collectionChain.addFirst(collection);
            collection = getRelatedItemBySubject(collection, hasCollection);
        }
        return collectionChain;
    }

    protected RegItem getRelatedItemBySubject(RegItem regItem, RegRelationpredicate predicate) throws Exception {
        List<RegItem> list = getRelatedItemsBySubject(regItem, predicate);
        if (list != null && !list.isEmpty()) {
            return list.stream().findAny().orElse(null);
        }
        return null;
//        return getRelatedItemsBySubject(regItem, predicate).stream()
//                .findAny()
//                .orElse(null);
    }

    private List<RegItem> getRelatedItemsBySubject(RegItem regItem, RegRelationpredicate predicate) throws Exception {

        if (regRelationManager != null && regItem != null && predicate != null
                && regRelationManager.getAllByRegItemSubjectAndPredicate(regItem, predicate) != null) {
            return regRelationManager.getAllByRegItemSubjectAndPredicate(regItem, predicate).stream()
                    .map(rel -> rel.getRegItemObject())
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    private List<RegItem> getRelatedItemsByObject(RegItem regItem, RegRelationpredicate predicate) throws Exception {

        if (regRelationManager != null && regItem != null && predicate != null
                && regRelationManager.getAllByRegItemObjectAndPredicate(regItem, predicate) != null) {
            List<RegRelation> relations = regRelationManager.getAllByRegItemObjectAndPredicate(regItem, predicate);
            List<RegItem> subjects = new ArrayList<>();
            relations.forEach((relation) -> {
                subjects.add(relation.getRegItemSubject());
            });
            return subjects;
        } else {
            return null;
        }
    }

}
