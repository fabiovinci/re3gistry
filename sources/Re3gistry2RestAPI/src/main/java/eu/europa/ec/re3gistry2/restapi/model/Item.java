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
package eu.europa.ec.re3gistry2.restapi.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.io.Serializable;

/**
 * Common basic information about any registry item
 */
@JsonPropertyOrder({
    "uri",
    "localid",
    "version",
    "versionHistory",
    "language",
    "type",
    "insertDate",
    "editDate",
    "itemclass",
    "registry",
    "register",
    "properties",
    "containedItems"
})
public class Item extends ContainedItem implements Serializable {

   
    private ItemRef registry;
    private ItemRef register;
    private List<ContainedItem> containedItems;

    public ItemRef getRegistry() {
        return registry;
    }

    public void setRegistry(ItemRef registry) {
        this.registry = registry;
    }

    public ItemRef getRegister() {
        return register;
    }

    public void setRegister(ItemRef register) {
        this.register = register;
    }

    public List<ContainedItem> getContainedItems() {
        return containedItems;
    }

    public void setContainedItems(List<ContainedItem> containedItems) {
        this.containedItems = containedItems;
    }

}
