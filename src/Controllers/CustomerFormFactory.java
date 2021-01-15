package Controllers;

import Models.Country;
import Models.Customer;
import Models.Division;
import Models.Record;

import java.util.Map;
import java.util.function.Consumer;

public final class CustomerFormFactory extends FormFactory<Customer, CustomerForm> {
    private Map<Long, Division> divisionMap;
    private Map<Long, Country> countryMap;

    public CustomerFormFactory(Class<Customer> modelClass) {
        super(modelClass);
    }

    /**
     * @see FormFactory#getInstance(Mode, Record, Consumer)
     */
    @Override
    public CustomerForm getInstance(Mode mode, Customer record, Consumer<Customer> callback) {
        return new CustomerForm(getTitle(mode), divisionMap, countryMap, mode, record, callback);
    }

    /**
     * sets the division map that is passed to every form controller instance. it prevents excessive sql requests
     *
     * @param divisionMap a map of divisionId to division models
     */
    public void setDivisionMap(Map<Long, Division> divisionMap) {
        this.divisionMap = divisionMap;
    }

    /**
     * sets the country map that is passed to every form controller instance. it prevents excessive sql requests
     *
     * @param countryMap a map of countryId to country models
     */
    public void setCountryMap(Map<Long, Country> countryMap) {
        this.countryMap = countryMap;
    }
}
