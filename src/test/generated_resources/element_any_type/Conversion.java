
package element_any_type;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="accept" type="{}List"/&gt;
 *         &lt;element name="return" type="{}Map"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "accepts",
    "returns"
})
@XmlRootElement(name = "conversion")
public class Conversion {

    @XmlElementWrapper(name = "accept", required = true)
    @XmlElement(name = "elements", nillable = true)
    protected List<Object> accepts = new ArrayList<Object>();
    @XmlElementWrapper(name = "return", required = true)
    @XmlElement(name = "entry", nillable = true)
    protected List<Entry> returns = new ArrayList<Entry>();

    public List<Object> getAccepts() {
        return accepts;
    }

    public void setAccepts(List<Object> accepts) {
        this.accepts = accepts;
    }

    public List<Entry> getReturns() {
        return returns;
    }

    public void setReturns(List<Entry> returns) {
        this.returns = returns;
    }

}
